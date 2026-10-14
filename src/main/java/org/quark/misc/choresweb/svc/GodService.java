package org.quark.misc.choresweb.svc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.qommons.QommonsUtils;
import org.qommons.fn.FunctionUtils;
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
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.util.BatchEntityOperator;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GodService {
	private final UserRepo theUserRepo;
	private final MembershipRepo theMembershipRepo;
	private final PointChangeRecordRepo theHistoryRepo;
	private final OrgsRepo theOrgRepo;
	private final JobRepo theJobRepo;
	private final PointResourceRepo theResourceRepo;
	private final AssignmentRepo theAssignmentRepo;
	private final UserService theUserService;
	private final ApplicationInfoService theAppInfoService;
	private final EntityMutationNotificationService theNotificationService;
	private final EntityManager theEntityManager;

	private final BatchEntityOperator<User, Long> theUserOperator;
	private final BatchEntityOperator<Organization, Long> theOrgOperator;

	public GodService(UserRepo userRepo, MembershipRepo membershipRepo, PointChangeRecordRepo historyRepo, OrgsRepo orgRepo,
		JobRepo jobRepo, PointResourceRepo resourceRepo, AssignmentRepo assignmentRepo, UserService userService,
		ApplicationInfoService appInfoService, EntityMutationNotificationService notificationService, EntityManager entityManager) {
		theUserRepo = userRepo;
		theMembershipRepo = membershipRepo;
		theHistoryRepo = historyRepo;
		theOrgRepo = orgRepo;
		theJobRepo = jobRepo;
		theResourceRepo = resourceRepo;
		theAssignmentRepo = assignmentRepo;
		theUserService = userService;
		theAppInfoService = appInfoService;
		theNotificationService = notificationService;
		theEntityManager = entityManager;

		theUserOperator = new BatchEntityOperator<>(theUserRepo, theEntityManager, User::getId,
			user -> !theUserService.isGodEternal(user.getEmail()));
		theOrgOperator = new BatchEntityOperator<>(theOrgRepo, theEntityManager, Organization::getId, FunctionUtils.TRUE);
	}

	@Transactional
	public void modifyUsers(List<Long> selectedUserIds, BulkUserModification modify) {
		theUserOperator.operate(selectedUserIds, new BatchUserModOperation(modify), 500);
	}

	@Transactional
	public void modifyUsers(Specification<User> filter, BulkUserModification modify) {
		List<Specification<User>> modApplies = new ArrayList<>();
		if (modify.god != null)
			modApplies.add(UserRepo.isGod(!modify.god));
		if (modify.globalAdmin != null)
			modApplies.add(UserRepo.isGlobalAdmin(!modify.globalAdmin));
		if (modify.blocked != null)
			modApplies.add(UserRepo.isBlocked(!modify.blocked));

		Specification<User> fullQuery = Specification.allOf(filter, Specification.anyOf(modApplies))//
			.and(Specification.not(UserRepo.emailIn(theUserService.getGodList()))); // Protect God(s) eternal

		theUserOperator.operate(theUserRepo.findAllIds(fullQuery), new BatchUserModOperation(modify), 500);
	}

	class BatchUserModOperation implements BatchEntityOperator.Operation<User, Long> {
		private final BulkUserModification modify;

		public BatchUserModOperation(BulkUserModification modify) {
			this.modify = modify;
		}

		@Override
		public void operate(Collection<User> entities, Collection<Long> ids) {
			// Perform the modification on the entity so the fired event is accurate,
			// but the persistence of the data will be done with a bulk operation at the end.
			// This is out-of-order, but still safe because this notification isn't received until after the transaction commits.
			for (User user : entities)
				theNotificationService.publishMutation("user", true, modify.modify(user));

			if (modify.god != null)
				theUserRepo.setGodWhereIdIn(ids, modify.god);
			if (modify.globalAdmin != null)
				theUserRepo.setGlobalAdminWhereIdIn(ids, modify.globalAdmin);
			if (modify.blocked != null)
				theUserRepo.setBlockedWhereIdIn(ids, modify.blocked);
		}
	}

	class BatchUserDelete implements BatchEntityOperator.Operation<User, Long> {
		private final Set<Long> orgIdsToCheck = new HashSet<>();

		@Override
		public void operate(Collection<User> entities, Collection<Long> ids) {
			for (User user : entities) {
				List<Membership> memberships = theMembershipRepo.getMembership(user);
				for (Membership member : memberships) {
					if (member.isManager())
						orgIdsToCheck.add(member.getOrganization().getId());
					theNotificationService.publishMutation("membership", false, ApiMembership.of(member));
				}
				theMembershipRepo.deleteAll(memberships);
				theNotificationService.publishMutation("user", false, ApiUser.of(user));
			}
			theHistoryRepo.deleteForUsers(ids);
			theAssignmentRepo.deleteForUsers(ids);
			theUserRepo.deleteAllById(ids);
		}

		public List<Long> getOrgIdsToCheck() {
			return QommonsUtils.unmodifiableCopy(orgIdsToCheck);
		}
	}

	class OrgManagerCheck implements BatchEntityOperator.Operation<Organization, Long> {
		@Override
		public void operate(Collection<Organization> orgs, Collection<Long> orgIds) {
			List<Long> orgIdsToDelete = null;
			for (Organization org : orgs) {
				List<Membership> members = theMembershipRepo.getMembership(org);
				if (members.stream().noneMatch(Membership::isManager)) {
					// No more managers. Delete the org.
					for (Membership member : members)
						theNotificationService.publishMutation("membership", false, ApiMembership.of(member));
					theMembershipRepo.deleteAll(members);
					if (orgIdsToDelete == null)
						orgIdsToDelete = new ArrayList<>();
					orgIdsToDelete.add(org.getId());
					theNotificationService.publishMutation("organization", false, ApiOrg.of(org));
				}
			}

			if (orgIdsToDelete != null) {
				theHistoryRepo.deleteForOrgs(orgIdsToDelete);
				theAssignmentRepo.deleteForOrgs(orgIdsToDelete);
				theJobRepo.deleteForOrgs(orgIdsToDelete);
				theResourceRepo.deleteForOrgs(orgIdsToDelete);
				theOrgRepo.deleteAllById(orgIdsToDelete);
			}
		}
	}

	@Transactional
	public void deleteUsers(List<Long> selectedUserIds) {
		BatchUserDelete delete = new BatchUserDelete();
		theUserOperator.operate(selectedUserIds, delete, 500);
		theOrgOperator.operate(delete.getOrgIdsToCheck(), new OrgManagerCheck(), 500);
	}

	@Transactional
	public void deleteUsers(Specification<User> filter) {
		//Protect God(s) eternal
		Specification<User> targetQuery = filter
			.and(Specification.not(UserRepo.emailIn(theUserService.getGodList())));

		deleteUsers(theUserRepo.findAllIds(targetQuery));
	}

	@Transactional
	public void setOpenToWorld(boolean open) {
		theAppInfoService.getApplicationInfo().setOpenToWorld(open);
		theAppInfoService.applicationInfoChanged();
	}
}
