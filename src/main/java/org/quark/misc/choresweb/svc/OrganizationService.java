package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiOrg;
import org.quark.misc.choresweb.api.ApiUser;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.OrgsRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.sync.SyncDataSource;
import org.quark.misc.choresweb.sync.SyncService;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class OrganizationService {
	private final OrgsRepo theOrgRepo;
	private final UserService theUserSvc;
	private final MembershipRepo theMembershipRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final AssignmentRepo theAssnRepo;
	private final JobRepo theJobRepo;
	private final PointResourceRepo theResourceRepo;
	private final EntityMutationNotificationService theNotificationSvc;

	public OrganizationService(OrgsRepo orgRepo, UserService userSvc, MembershipRepo membershipRepo, PointChangeRecordRepo pointChangeRepo,
		AssignmentRepo assnRepo, JobRepo jobRepo, PointResourceRepo resourceRepo, EntityMutationNotificationService notificationSvc,
		SyncService<User> syncService, ObjectMapper objectMapper) {
		theOrgRepo = orgRepo;
		theUserSvc = userSvc;
		theMembershipRepo = membershipRepo;
		thePointChangeRepo = pointChangeRepo;
		theAssnRepo = assnRepo;
		theJobRepo = jobRepo;
		theResourceRepo = resourceRepo;
		theNotificationSvc = notificationSvc;

		// Hook Organization into the synchronization architecture
		theNotificationSvc.installSerializer("organization", ApiOrg.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiOrg.class, objectMapper));
		syncService
		.installDataSource(new SyncDataSource.AbstractReflectedDataSource<User, ApiOrg>("organization", ApiOrg.class, objectMapper) {
			@Override
			public ValidationMaintainer<User> validateSubscription(User user, Map<String, SyncDataFilter<ApiOrg>> filters)
				throws UnsupportedOperationException {
				// Only God may see users they are not a member of
				if (!user.isGod())
					throw new UnsupportedOperationException("You do not have permission to query organizations");
				long userId = user.getId(); // Release the user object to the GC
				return event -> {
					if (event.getEntity() instanceof ApiUser) {
						ApiUser eventUser = (ApiUser) event.getEntity();
						if (eventUser.id() == userId) {
							if (!event.isPresent() || !eventUser.god())
								return ValidationChange.RevokeSubscription;
						}
					}
					return ValidationChange.Ignore;
				};
			}

			@Override
			public Collection<ApiOrg> queryEntities(List<Map<String, SyncDataFilter<ApiOrg>>> filters) {
				return theOrgRepo.findAll().stream()//
					.map(ApiOrg::of)//
					.filter(org -> SyncDataSource.passesAny(org, filters))//
					.toList();
			}
		});

		// Hook Membership into the synchronization architecture
		theNotificationSvc.installSerializer("membership", ApiMembership.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiMembership.class, objectMapper));
		syncService.installDataSource(
			new SyncDataSource.AbstractReflectedDataSource<User, ApiMembership>("membership", ApiMembership.class, objectMapper) {
				@Override
				public ValidationMaintainer<User> validateSubscription(User user, Map<String, SyncDataFilter<ApiMembership>> filters)
					throws UnsupportedOperationException {
					// 2 types of queries supported:
					Long queryUser = SyncDataSource.getConstantQueryBy(filters, "member");
					Long queryOrg = SyncDataSource.getConstantQueryBy(filters, "organization");
					if (queryUser != null && user.getId() == queryUser.longValue()) {
						// The user can query their own memberships across all organizations
						// This query is always available regardless of permissions
						return _ -> ValidationChange.Ignore;
					} else if (queryOrg != null) {
						// The user can query all members of a single organization they are a member of
						Membership me = getMembership(user, queryOrg);
						if (me == null)
							throw new UnsupportedOperationException(
								"No such organization with ID " + queryOrg + " or you are not a member of it");
						long userId = user.getId(); // Release the user object to garbage collection
						long orgId = queryOrg;
						// This query is valid as long as the organization exists and the user is a member
						return event -> {
							if (!event.isPresent() && event.getEntity() instanceof ApiMembership membership) {
								if (membership.member().id() == userId && membership.organization().id() == orgId) {
									// The user's membership in the organization has been revoked
									return ValidationChange.RevokeSubscription;
								}
							} else if (!event.isPresent() && event.getEntity() instanceof ApiOrg
								&& ((ApiOrg) event.getEntity()).id() == orgId) {
								return ValidationChange.RevokeSubscription;
							}
							return ValidationChange.Ignore;// Still valid
						};
					} else
						throw new UnsupportedOperationException(
							"Only 2 types of membership queries are supported:\n" + "1) Your own memberships across all organizations\n"
								+ "and 2) All members of a single organization you are a member of");
				}

				@Override
				public Collection<ApiMembership> queryEntities(List<Map<String, SyncDataFilter<ApiMembership>>> filters) {
					Set<Long> queriedOrgs = null, queriedUsers = null;
					Map<Long, ApiMembership> members = new HashMap<>();
					for (var subFilters : filters) {
						Long queryOrg = SyncDataSource.getConstantQueryBy(subFilters, "organization");
						Long queryUser = SyncDataSource.getConstantQueryBy(subFilters, "member");
						if (queryOrg != null) {
							if (queriedOrgs == null)
								queriedOrgs = new HashSet<>();
							if (queriedOrgs.add(queryOrg))
								addMembers(members, theMembershipRepo.getByOrganizationId(queryOrg));
						} else {
							if (queriedUsers == null)
								queriedUsers = new HashSet<>();
							if (queriedUsers.add(queryUser))
								addMembers(members, theMembershipRepo.getByMemberId(queryUser));
						}
					}
					return members.values();
				}

				private void addMembers(Map<Long, ApiMembership> members, List<Membership> newMembers) {
					newMembers.stream()//
					.filter(membership -> !members.containsKey(membership.getId()))//
					// Even though the subscriber doesn't care about the organization piece,
					// other potential subscriptions might, so we need to include it
					.map(membership -> ApiMembership.of(membership, true, true))//
					.forEach(membership -> members.put(membership.id(), membership));
				}
			});

	}

	@Transactional(readOnly = true)
	public List<Membership> getAvailableOrgs(User user) {
		return theMembershipRepo.getMembership(user);
	}

	@Transactional(readOnly = true)
	public Membership getMembershipById(Jwt user, long id) {
		Membership member = theMembershipRepo.findById(id).orElse(null);
		if (member == null)
			return null;
		getMe(user, member.getOrganization().getId());
		return member;
	}

	@Transactional
	public Membership getMe(Jwt authUser, long orgId) {
		User user = theUserSvc.getMe(authUser);
		if (user == null)
			throw new NoSuchElementException("Organization does not exist or you are not a member");
		theUserSvc.userActive(user);
		Membership membership = theMembershipRepo.getMembership(user.getId(), orgId);
		if (membership == null)
			throw new NoSuchElementException("Organization does not exist or you are not a member");
		return membership;
	}

	@Transactional(readOnly = true)
	public Membership getOrganization(String userEmail, long id) {
		User user = theUserSvc.getUser(userEmail);
		if (user == null)
			return null;
		theUserSvc.userActive(user);
		Membership membership = theMembershipRepo.getMembership(user.getId(), id);
		if (membership == null)
			throw new NoSuchElementException("Organization does not exist or you are not a member");
		return membership;
	}

	@Transactional(readOnly = true)
	public Membership getMembership(User user, long orgId) {
		return theMembershipRepo.getByMemberAndOrganizationId(user, orgId);
	}

	@Transactional
	public Membership addOrganization(String userEmail) {
		User user = theUserSvc.getUserCreateIfGod(userEmail);
		if (user == null || !user.isGlobalAdmin())
			throw new UnsupportedOperationException("You do not have permission to create organizations");
		String newName = ChoresWebUtils.getNewName(theOrgRepo.findAll(), 200, "Org");
		Organization org = new Organization(newName);
		theOrgRepo.save(org);
		theUserSvc.userActive(user);
		Membership membership = new Membership(org, user);
		membership.setName(userEmail);
		membership.setManager(true);
		membership.setLastActive(Instant.now());
		theMembershipRepo.save(membership);
		theNotificationSvc.publishMutation("organization", true, ApiOrg.of(org));
		theNotificationSvc.publishMutation("membership", true, ApiMembership.of(membership, true, true));
		return membership;
	}

	@Transactional
	public void setOrganizationName(Membership member, String name) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify this organization");
		if (name.equals(member.getOrganization().getName()))
			return;
		if (name.length() < 3 || name.length() > Organization.NAME_LENGTH)
			throw new IllegalArgumentException("Organization name must be between 3 and " + Organization.NAME_LENGTH + " characters");
		else if (!name.trim().equals(name))
			throw new IllegalArgumentException("Organization name cannot start or end with white space");
		else if (theOrgRepo.getByName(name) > 0)
			throw new IllegalArgumentException("A different organization named '" + name + "' exists");
		member.getOrganization().setName(name);
		theOrgRepo.save(member.getOrganization());
		theNotificationSvc.publishMutation("organization", true, ApiOrg.of(member.getOrganization()));
	}

	@Transactional
	public void deleteOrganization(Membership member) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to delete this organization");
		thePointChangeRepo.deleteByOrganization(member.getOrganization());
		theAssnRepo.deleteByOrganization(member.getOrganization());
		theJobRepo.deleteByOrganization(member.getOrganization());
		theResourceRepo.deleteByOrganization(member.getOrganization());
		theMembershipRepo.deleteByOrganization(member.getOrganization());
		theOrgRepo.delete(member.getOrganization());
		theNotificationSvc.publishMutation("organization", false, ApiOrg.of(member.getOrganization()));
	}
}
