package org.quark.misc.choresweb.svc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
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
import org.quark.misc.choresweb.api.ApiOrg;
import org.quark.misc.choresweb.api.ApiUser;
import org.quark.misc.choresweb.entities.ApplicationInfo;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.OrgsRepo;
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
	private final OrgsRepo theOrgsRepo;
	private final MembershipRepo theMembershipRepo;
	private final AssignmentRepo theAssnRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final EntityMutationNotificationService theNotificationSvc;
	private final ApplicationInfo theAppInfo;

	public UserService(UserRepo userRepo, OrgsRepo orgsRepo, MembershipRepo membershipRepo, AssignmentRepo assnRepo,
		PointChangeRecordRepo pointChangeRepo, EntityMutationNotificationService notificationSvc, SyncService<User> syncService,
		ObjectMapper objectMapper, ApplicationInfoService appInfo) {
		theUserRepo = userRepo;
		theOrgsRepo = orgsRepo;
		theMembershipRepo = membershipRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;
		theNotificationSvc = notificationSvc;
		theAppInfo = appInfo.getApplicationInfo();

		theGodList = new HashSet<>();

		theNotificationSvc.installSerializer("user", ApiUser.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiUser.class, objectMapper));
		syncService.installDataSource(new SyncDataSource.AbstractReflectedDataSource<User, ApiUser>("user", ApiUser.class, objectMapper) {
			@Override
			public ValidationMaintainer<User> validateSubscription(User user, Map<String, SyncDataFilter<ApiUser>> filters)
				throws UnsupportedOperationException {
				Long queryUserId = SyncDataSource.getConstantQueryBy(filters, "id");
				if (queryUserId != null && queryUserId.longValue() == user.getId()) {
					// Anybody can watch their own user, and that can't be taken away
					return _ -> ValidationChange.Ignore;
				} else { // Only God can query any other users, and they can see everything
					if (!user.isGod())
						throw new UnsupportedOperationException("You do not have permission to see user changes");
					long userId = user.getId();
					return event -> {
						if (event.getEntity() instanceof User) {
							User eventUser = (User) event.getEntity();
							if (eventUser.getId() == userId) {
								if (!event.isPresent() || !eventUser.isGod())
									return ValidationChange.RevokeSubscription;
							}
						}
						return ValidationChange.Ignore;
					};
				}
			}

			@Override
			public List<ApiUser> queryEntities(List<Map<String, SyncDataFilter<ApiUser>>> filters) {
				// We're making a lot of assumptions here for performance:
				// The filters have passed the validation above, so we know that either it's a user's query for their own filter,
				// or they're God.
				// God can query the full set of users from the god service if they wish, but we don't want to assume they want that
				// and spam them with the full set of users as soon as they subscribe.
				// So handle these cases in 3 cases:
				// * If there are filters that are not trivial and not specific to ID, then we have no choice
				// but to materialize all the entities and filter them
				// * If there are by-ID filters, satisfy them with a batch call
				// * Otherwise return empty
				Set<Long> byId = new HashSet<>();
				boolean hasGeneralFilter = false;
				for (Map<String, SyncDataFilter<ApiUser>> filter : filters) {
					if (filter.isEmpty())
						continue;
					else if (filter.size() == 1) {
						Long id = SyncDataSource.getConstantQueryBy(filter, "id");
						if (id != null) {
							byId.add(id);
							continue;
						}
					}
					hasGeneralFilter = true;
					break;
				}

				if (hasGeneralFilter) {
					List<Map<String, SyncDataFilter<ApiUser>>> nonTrivialFilters = filters.stream()//
						.filter(f -> !f.isEmpty())//
						.toList();
					return QommonsUtils.mapAndFilter(theUserRepo.findAll(), //
						ApiUser::of, //
						entity -> SyncDataSource.passesAny(entity, nonTrivialFilters)//
						);
				} else if (!byId.isEmpty())
					return QommonsUtils.filterMap(theUserRepo.findAllById(byId), null, ApiUser::of);
				else
					return Collections.emptyList();
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
		User dbUser = getUserCreateIfConfigured(email);
		if (dbUser != null) {
			if (dbUser.isBlocked())
				throw new NoSuchElementException("You do not have permission to access this service");
			Instant now = Instant.now();
			if (dbUser.getLastActive() == null || TimeUtils.between(dbUser.getLastActive(), now).getSeconds() >= 10) {
				dbUser.setLastActive(now);
				theUserRepo.save(dbUser);
				theNotificationSvc.publishMutation("user", true, ApiUser.of(dbUser));
			}
		}
		return dbUser;
	}

	public static String getUserEmail(Jwt user) {
		String email = user.getClaimAsString("email");
		if (email == null)
			email = user.getSubject();
		return email;
	}

	public boolean isGodEternal(String email) {
		return theGodList.contains(email.toLowerCase());
	}

	public Set<String> getGodList() {
		return Collections.unmodifiableSet(theGodList);
	}

	@Transactional
	public User getUserCreateIfConfigured(String email) {
		if (theAppInfo.isOpenToWorld())
			return getOrCreateUser(email);

		User found = theUserRepo.getByEmail(email);
		if (found == null && isGodEternal(email))
			return getOrCreateUser(email);
		return found;
	}

	@Transactional
	public User getOrCreateUser(String email) {
		User found = null;
		for (int tries = 0; tries < 5; tries++) {
			found = theUserRepo.getByEmail(email);
			if (found == null) {
				found = new User(email);
				int at = email.indexOf('@');
				found.setName(at > 0 ? email.substring(0, at) : email);
				boolean god = theGodList.contains(email.toLowerCase());
				found.setGod(god);
				found.setGlobalAdmin(god || theAppInfo.isOpenToWorld());

				try {
					theUserRepo.save(found);
					theNotificationSvc.publishMutation("user", true, ApiUser.of(found));
				} catch (RuntimeException e) {
					// Inserted by another call
					found = null;
				}
			}
		}
		return found;
	}

	@Transactional
	public User modifyUser(Jwt me, ModifyUserCommand modification) {
		String myEmail = getUserEmail(me);
		User self = getUserCreateIfConfigured(myEmail);
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
		if (modification.email() != null) {
			if (!self.isGod())
				throw new UnsupportedOperationException("Only God can change user emails");
			user.setEmail(modification.email());
		}
		if (modification.god() != null) {
			if (!modification.god() && theGodList.contains(user.getEmail().toLowerCase()))
				throw new UnsupportedOperationException("This user is God eternal");
			user.setGod(modification.god());
			if (modification.god())
				user.setGlobalAdmin(true);
		}
		if (modification.globalAdmin() != null) {
			if (!modification.globalAdmin() && theGodList.contains(user.getEmail().toLowerCase()))
				throw new UnsupportedOperationException("This user is God eternal");
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
		String name = user.getName();
		if (name.length() > 100)
			name = name.substring(0, 100);
		name = ChoresWebUtils.getNewName(theMembershipRepo.getMembership(me.getOrganization()), 100, name);
		membership.setName(name);
		membership.setWorker(true);
		if (configure != null)
			configure.accept(membership);
		theMembershipRepo.saveAll(Arrays.asList(membership, me));
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(me));
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(membership));
		updateOrg(me.getOrganization());
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
			theNotificationSvc.publishMutation("membership", true, ApiMembership.of(me));
			theNotificationSvc.publishMutation("membership", true, ApiMembership.of(member));
			updateOrg(me.getOrganization());
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
		thePointChangeRepo.deleteWorkerHistory(target);
		theAssnRepo.deleteForWorker(target);
		theMembershipRepo.delete(target);
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(me));
		theNotificationSvc.publishMutation("membership", false, ApiMembership.of(target));
		updateOrg(me.getOrganization());
	}

	public void updateOrg(Organization org) {
		Instant now = Instant.now();
		// This one doesn't happen as often because it involves a query
		if (org.getLastActive() == null || TimeUtils.between(org.getLastActive(), now).getSeconds() > 300) { // 5 minutes
			org.setLastActive(now);
			theOrgsRepo.save(org);
			theNotificationSvc.publishMutation("organization", true, ApiOrg.of(org));
			for (Membership member : theMembershipRepo.getMembership(org))
				theNotificationSvc.publishMutation("membership", true, ApiMembership.of(member));
		}
	}

	public void memberUpdated(Membership member) {
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(member));
	}
}
