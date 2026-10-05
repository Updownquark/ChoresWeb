package org.quark.misc.choresweb.svc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.TimeUtils;
import org.qommons.io.NativeFileSource;
import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ChoresApplicationEvent;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserService {
	@Value("${chores.whitelist}")
	private String theWhitelistPath;
	private final Set<String> theWhiteList;

	private final UserRepo theUserRepo;
	private final MembershipRepo theMembershipRepo;
	private final AssignmentRepo theAssnRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final ApplicationEventPublisher theEventPublisher;

	public UserService(UserRepo userRepo, MembershipRepo membershipRepo, AssignmentRepo assnRepo, PointChangeRecordRepo pointChangeRepo,
		ApplicationEventPublisher eventPublisher) {
		theUserRepo = userRepo;
		theMembershipRepo = membershipRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;
		theEventPublisher = eventPublisher;

		theWhiteList = new HashSet<>();
	}

	@PostConstruct
	private void init() {
		// Using the BetterFile API to resolve user-relative paths (~)
		try (BufferedReader in = new BufferedReader(
			new InputStreamReader(new NativeFileSource().at(theWhitelistPath).read(), StandardCharsets.UTF_8))) {
			theWhiteList.add(in.readLine().trim().toLowerCase());
		} catch (IOException e) {
			log.error("Could not load white list--org creation will be impossible", e);
		}
	}

	@Transactional
	public void userActive(User user) {
		Instant now = Instant.now();
		if (user.getLastActive() == null || TimeUtils.between(user.getLastActive(), now).getSeconds() > 30) {
			user.setLastActive(now);
			theUserRepo.save(user);
		}
	}

	@Transactional(readOnly = true)
	public User getUser(String email) {
		User found = theUserRepo.getByEmail(email);
		return found;
	}

	@Transactional
	public User getMe(Jwt user) {
		String email = getUserEmail(user);
		if (email == null)
			return null;
		return getUserCreateIfAdmin(email);
	}

	public static String getUserEmail(Jwt user) {
		String email = user.getClaimAsString("email");
		if (email == null)
			email = user.getSubject();
		return email;
	}

	@Transactional
	public User getUserCreateIfAdmin(String email) {
		User found = theUserRepo.getByEmail(email);
		if (found == null && canCreateOrgs(email)) {
			found = new User(email);
			theUserRepo.save(found);
		}
		return found;
	}

	@Transactional
	public User getOrCreateUser(String email) {
		User found = theUserRepo.getByEmail(email);
		if (found == null) {
			found = new User(email);
			theUserRepo.save(found);
		}
		return found;
	}

	public boolean canCreateOrgs(String userEmail) {
		return theWhiteList.contains(userEmail.toLowerCase());
	}

	@Transactional(readOnly = true)
	public Membership getMembership(long orgId, long userId) {
		return theMembershipRepo.getMembership(userId, orgId);
	}

	@Transactional(readOnly = true)
	public List<Membership> getMembers(Membership me) {
		return theMembershipRepo.getMembership(me.getOrganization());
	}

	@Transactional(readOnly = true)
	public List<ApiMembership> getApiMembers(Membership me) {
		return theMembershipRepo.getMembership(me.getOrganization()).stream()//
			.map(member -> ApiMembership.of(member, true, true))//
			.toList();
	}

	@Transactional
	public Membership addWorker(Membership me, String targetUserEmail, Consumer<Membership> configure) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to add members to this organization");
		User user = getOrCreateUser(targetUserEmail);
		Membership current = theMembershipRepo.getMembership(user.getId(), me.getOrganization().getId());
		if (current != null)
			return current;

		Membership membership = new Membership(me.getOrganization(), user);
		String name = user.getEmail();
		int at = name.indexOf('@');
		if (at > 0)
			name = name.substring(0, at);
		if (name.length() > 100)
			name = name.substring(0, 100);
		name = ChoresWebUtils.getNewName(theMembershipRepo.getMembership(me.getOrganization()), 100, name);
		membership.setName(name);
		membership.setWorker(true);
		membership.setLastActive(Instant.now());
		if (configure != null)
			configure.accept(membership);
		theMembershipRepo.save(membership);
		theEventPublisher.publishEvent(ChoresApplicationEvent.securityMutation(this, me.getOrganization().getId(), user.getId(), true));
		theEventPublisher
			.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "membership", membership.getId(), true));
		return membership;
	}

	@Transactional
	public Membership modifyWorker(Membership me, ModifyWorkerCommand command) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify members in this organization");
		Membership member = theMembershipRepo.getMembership(command.userId(), me.getOrganization().getId());
		if (member == null)
			throw new NoSuchElementException("No such member");

		boolean changed = false;
		if (command.manager() != null) {
			if (!command.manager() && member.getMember().getId() == me.getMember().getId())
				throw new IllegalArgumentException("You cannot take away your own manager status");
			if (command.manager().booleanValue() != member.isManager()) {
				member.setManager(command.manager());
				changed = true;
			}
		}
		boolean withName = member.getName() != null;
		if (withName) {
			if (member.getName().length() == 0)
				throw new IllegalArgumentException("Name cannot be empty");
			else if (member.getName().length() > 100)
				throw new IllegalArgumentException("Name cannot exceed 100");
		}

		if (changed) {
			theMembershipRepo.save(member);
			theEventPublisher.publishEvent(
				ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "membership", member.getId(), true));
		}
		return member;
	}

	@Transactional
	public void removeWorker(Membership me, long targetUser) {
		Membership target = theMembershipRepo.getMembership(targetUser, me.getOrganization().getId());
		if (target == null)
			throw new NoSuchElementException("No such member");
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to remove members from this organization");
		else if (me.getOrganization().getId() != target.getOrganization().getId())
			throw new UnsupportedOperationException("You must sign in to the organization you want to manage as a manager");
		thePointChangeRepo.deleteForMember(target);
		theAssnRepo.deleteForMember(target);
		theMembershipRepo.delete(target);
		theEventPublisher.publishEvent(
			ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "membership", target.getId(), false));
		theEventPublisher
			.publishEvent(ChoresApplicationEvent.securityMutation(this, me.getOrganization().getId(), target.getMember().getId(), false));
	}

	public void memberUpdated(Membership member) {
		theEventPublisher.publishEvent(
			ChoresApplicationEvent.dataChange(this, member.getOrganization().getId(), "membership", member.getId(), true));
	}
}
