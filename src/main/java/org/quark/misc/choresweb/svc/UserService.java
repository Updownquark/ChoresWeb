package org.quark.misc.choresweb.svc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.io.NativeFileSource;
import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiUser;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.sync.SyncDataSource;
import org.quark.misc.choresweb.sync.SyncService;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class UserService {
	@Value("${chores.godList}")
	private String theGodListPath;
	private final Set<String> theGodList;

	private final UserRepo theUserRepo;
	private final MembershipRepo theMembershipRepo;
	private final AssignmentRepo theAssnRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final EntityMutationNotificationService theNotificationSvc;

	public UserService(UserRepo userRepo, MembershipRepo membershipRepo, AssignmentRepo assnRepo, PointChangeRecordRepo pointChangeRepo,
		EntityMutationNotificationService notificationSvc, SyncService<User> syncService, ObjectMapper objectMapper) {
		theUserRepo = userRepo;
		theMembershipRepo = membershipRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;
		theNotificationSvc = notificationSvc;

		theGodList = new HashSet<>();

		theNotificationSvc.installSerializer("user", ApiUser.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiUser.class, objectMapper));
		syncService.installDataSource(new SyncDataSource.AbstractReflectedDataSource<User, ApiUser>("user", ApiUser.class, objectMapper) {
			@Override
			public ValidationMaintainer<User> validateSubscription(User user, Map<String, SyncDataFilter<ApiUser>> filters)
				throws UnsupportedOperationException {
				// Only Global admins can query users, and they can see everything
				if (!user.isGlobalAdmin())
					throw new UnsupportedOperationException("You do not have permission to see user changes");
				long userId = user.getId();
				return event -> {
					if (event.getEntity() instanceof User) {
						User eventUser = (User) event.getEntity();
						if (eventUser.getId() == userId) {
							if (!event.isPresent() || !eventUser.isGlobalAdmin())
								return ValidationChange.RevokeSubscription;
						}
					}
					return ValidationChange.Ignore;
				};
			}

			@Override
			public List<ApiUser> queryEntities(List<Map<String, SyncDataFilter<ApiUser>>> filters) {
				return QommonsUtils.mapAndFilter(theUserRepo.findAll(), //
					ApiUser::of, //
					entity -> SyncDataSource.passesAny(entity, filters)//
					);
			}
		});
	}

	@PostConstruct
	private void init() {
		// Using the BetterFile API to resolve user-relative paths (~)
		try (BufferedReader in = new BufferedReader(
			new InputStreamReader(new NativeFileSource().at(theGodListPath).read(), StandardCharsets.UTF_8))) {
			String email = in.readLine().trim().toLowerCase();
			if (!email.isEmpty())
				theGodList.add(email);
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
		return getUserCreateIfGod(email);
	}

	public static String getUserEmail(Jwt user) {
		String email = user.getClaimAsString("email");
		if (email == null)
			email = user.getSubject();
		return email;
	}

	@Transactional
	public User getUserCreateIfGod(String email) {
		User found = theUserRepo.getByEmail(email);
		if (found == null && theGodList.contains(email.toLowerCase())) {
			found = new User(email);
			found.setGod(true);
			found.setGlobalAdmin(true);
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

	@Transactional
	public User modifyUser(Jwt me, ModifyUserCommand modification) {
		String myEmail = getUserEmail(me);
		User self = getUserCreateIfGod(myEmail);
		if (self == null)
			throw new UnsupportedOperationException("You are not registered as a user on this application");
		boolean isSelf = self.getId() == modification.id();
		if (!isSelf && !self.isGod())
			throw new UnsupportedOperationException("You do not have permission to modify a user that is not yourself");

		User user = theUserRepo.findById(modification.id()).orElse(null);
		if (user == null) {
			if (self.isGod())
				throw new NoSuchElementException("No such user with ID " + modification.id());
			else
				throw new UnsupportedOperationException("You do not have permission to modify a user that is not yourself");
		}

		if (!self.isGod() && (modification.god() || modification.globalAdmin())) {
			throw new UnsupportedOperationException("You do not have permission to change this user's permissions");
		}

		// Permissions checks done. Now do the modification.
		if (modification.email() != null)
			user.setEmail(modification.email());
		if (modification.god() != null) {
			user.setGod(modification.god());
			if (modification.god())
				user.setGlobalAdmin(true);
		}
		if (modification.globalAdmin() != null) {
			user.setGlobalAdmin(modification.globalAdmin());
			if (!modification.globalAdmin())
				user.setGod(false);
		}
		theUserRepo.save(user);
		theNotificationSvc.publishMutation("user", true, ApiUser.of(user));
		return user;
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
			.map(member -> ApiMembership.of(member))//
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
		me.setLastActive(Instant.now());
		if (configure != null)
			configure.accept(membership);
		theMembershipRepo.saveAll(Arrays.asList(membership, me));
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(me));
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(membership));
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
			me.setLastActive(Instant.now());
			theMembershipRepo.saveAll(Arrays.asList(member, me));
			theNotificationSvc.publishMutation("membership", true, ApiMembership.of(me));
			theNotificationSvc.publishMutation("membership", true, ApiMembership.of(member));
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
		me.setLastActive(Instant.now());
		theMembershipRepo.save(me);
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(me));
		theNotificationSvc.publishMutation("membership", false, ApiMembership.of(target));
	}

	public void memberUpdated(Membership member) {
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(member));
	}
}
