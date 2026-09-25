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

import org.qommons.TimeUtils;
import org.qommons.io.NativeFileSource;
import org.quark.misc.choresweb.api.ProtoMembership;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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

	private final EntityChangeSet<BinaryId, OrgGroupedMember> theChanges = new EntityChangeSet<>(member -> member.id, 15000);

	public UserService(UserRepo userRepo, MembershipRepo membershipRepo, AssignmentRepo assnRepo, PointChangeRecordRepo pointChangeRepo) {
		theUserRepo = userRepo;
		theMembershipRepo = membershipRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;

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
	public List<PointChangeRecord.FullPcrDto> getWorkerHistory(Membership me, Membership target, int pageNumber, int pageSize) {
		if (me.getId().getOrganization().getId() != target.getId().getOrganization().getId())
			throw new UnsupportedOperationException("You must sign in to the organization you want to view");
		return thePointChangeRepo.getWorkerHistory(target, PageRequest.of(pageNumber, pageSize)).getContent();
	}

	@Transactional(readOnly = true)
	public List<Membership> getMembers(Membership me) {
		return theMembershipRepo.getMembership(me.getId().getOrganization());
	}

	@Transactional(readOnly = true)
	public EntityChangeSet.ChangeSet<ProtoMembership> getApiMembers(Membership me) {
		return theChanges.getValues(() -> theMembershipRepo.getMembership(me.getId().getOrganization()).stream()//
			.map(member -> ProtoMembership.of(member, true, true))//
			.toList());
	}

	@Transactional
	public Membership addWorker(Membership me, String targetUserEmail) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to add members to this organization");
		User user = getOrCreateUser(targetUserEmail);
		Membership current = theMembershipRepo.getMembership(user.getId(), me.getId().getOrganization().getId());
		if (current != null)
			return current;

		Membership membership = new Membership(me.getId().getOrganization(), user);
		membership.setWorker(true);
		membership.setLastActive(Instant.now());
		theMembershipRepo.save(membership);
		theChanges.changed(new OrgGroupedMember(membership.getId().getOrganization().getId(), ProtoMembership.of(membership, false, true)));
		return membership;
	}

	@Transactional
	public Membership modifyWorker(Membership me, ModifyWorkerCommand command) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify members in this organization");
		Membership member = theMembershipRepo.getMembership(command.userId(), me.getId().getOrganization().getId());
		if (member == null)
			throw new NoSuchElementException("No such member");

		boolean changed = false;
		if (command.manager() != null) {
			if (!command.manager() && member.getId().getMember().getId() == me.getId().getMember().getId())
				throw new IllegalArgumentException("You cannot take away your own manager status");
			if (command.manager().booleanValue() != member.isManager()) {
				member.setManager(command.manager());
				changed = true;
			}
		}

		if (changed) {
			theMembershipRepo.save(member);
			theChanges.changed(new OrgGroupedMember(member.getId().getOrganization().getId(), ProtoMembership.of(member, false, true)));
		}
		return member;
	}

	@Transactional
	public void removeWorker(Membership me, long targetUser) {
		Membership target = theMembershipRepo.getMembership(targetUser, me.getId().getOrganization().getId());
		if (target == null)
			throw new NoSuchElementException("No such member");
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to remove members from this organization");
		else if (me.getId().getOrganization().getId() != target.getId().getOrganization().getId())
			throw new UnsupportedOperationException("You must sign in to the organization you want to manage as a manager");
		thePointChangeRepo.deleteForMember(target);
		theAssnRepo.deleteForMember(target);
		theMembershipRepo.delete(target);
		theChanges.changed(new OrgGroupedMember(target.getId().getOrganization().getId(), ProtoMembership.deleted(target, false, true)));
	}

	public void memberUpdated(Membership member) {
		theChanges.changed(new OrgGroupedMember(member.getId().getOrganization().getId(), ProtoMembership.of(member, false, true)));
	}

	public EntityChangeSet.ChangeSet<ProtoMembership> getChanges(long orgId, long lastKnownChange) {
		return theChanges.getChanges(lastKnownChange, member -> member.orgId == orgId, member -> member.member);
	}

	static class OrgGroupedMember {
		final long orgId;
		final BinaryId id;
		final ProtoMembership member;

		OrgGroupedMember(long orgId, ProtoMembership member) {
			this.orgId = orgId;
			this.id = new BinaryId(orgId, member.member().id());
			this.member = member;
		}
	}
}
