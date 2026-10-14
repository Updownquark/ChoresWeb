package org.quark.misc.choresweb.svc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CleanUpService {
	private final UserRepo theUserRepo;
	private final OrgsRepo theOrgRepo;
	private final MembershipRepo theMembersRepo;
	private final PointChangeRecordRepo theHistoryRepo;
	private final AssignmentRepo theAssignmentRepo;
	private final JobRepo theJobRepo;
	private final PointResourceRepo theResourceRepo;
	private final EntityMutationNotificationService theNotificationSvc;
	private final EntityManager theEntityManager;

	/** Tolerance for inactive items */
	private final Duration theInactivityTolerance;
	/** Tolerance for inactive items that have never been used */
	private final Duration theUnusedInactivityTolerance;

	public CleanUpService(UserRepo userRepo, OrgsRepo orgRepo, MembershipRepo membersRepo, PointChangeRecordRepo historyRepo,
		AssignmentRepo assignmentRepo, JobRepo jobRepo, PointResourceRepo resourceRepo, EntityMutationNotificationService notificationSvc,
		EntityManager entityManager) {
		theUserRepo = userRepo;
		theOrgRepo = orgRepo;
		theMembersRepo = membersRepo;
		theHistoryRepo = historyRepo;
		theAssignmentRepo = assignmentRepo;
		theJobRepo = jobRepo;
		theResourceRepo = resourceRepo;
		theNotificationSvc = notificationSvc;
		theEntityManager = entityManager;

		theUnusedInactivityTolerance = Duration.ofDays(30);
		theInactivityTolerance = Duration.ofDays(180);
	}

	@Transactional(timeout = 600) //
	@Scheduled(cron = "0 0 9 * * *") // Every day at 0900
	public void runPurge() {
		Instant now = Instant.now();
		StringBuilder purgeReport = new StringBuilder("Running Purge:");
		Instant inactiveDate = now.minus(theInactivityTolerance);
		Instant unusedInactiveDate = now.minus(theUnusedInactivityTolerance);
		int[] purged = { 0 };

		// First, purge memberships
		List<Membership> membersToPurge = new ArrayList<>();
		Organization[] prevOrg = { null };
		Set<User> mtpUsers = new HashSet<>();
		try (Stream<Membership> members = theMembersRepo.getStaleMemberships(unusedInactiveDate)) {
			members.forEach(member -> {
				if (prevOrg[0] != member.getOrganization()) {
					if (!membersToPurge.isEmpty()) {
						purgeMembers(membersToPurge);
						for (User user : mtpUsers)
							theEntityManager.detach(user);
						mtpUsers.clear();
					}
					if (prevOrg[0] != null)
						theEntityManager.detach(prevOrg[0]);
					prevOrg[0] = member.getOrganization();
				}

				if (member.getLastActive().compareTo(inactiveDate) <= 0//
					|| (!member.isManager() && member.getPoints() == 0)) {
					purged[0]++;
					membersToPurge.add(member);
					mtpUsers.add(member.getMember());
				} else {
					theEntityManager.detach(member);
					if (!mtpUsers.contains(member.getMember()))
						theEntityManager.detach(member.getMember());
				}
			});
		}
		if (!membersToPurge.isEmpty()) {
			purgeMembers(membersToPurge);
			membersToPurge.clear();
			mtpUsers.clear();
		}
		theEntityManager.flush();
		theEntityManager.clear();

		purgeReport.append("\n\t").append(purged[0]).append(" Memberships purged");

		// Purge organizations with no members now that memberships have been purged
		int batchSize = 100;
		purged[0] = 0;
		List<Organization> orgsToPurge = new ArrayList<>();
		try (Stream<Organization> orgs = theOrgRepo.getOrphanedOrgs()) {
			orgs.forEach(org -> {
				purged[0]++;
				theNotificationSvc.publishMutation("organization", false, ApiOrg.of(org));
				theJobRepo.deleteByOrganization(org);
				theResourceRepo.deleteByOrganization(org);
				orgsToPurge.add(org);
				if (orgsToPurge.size() > batchSize) {
					theOrgRepo.deleteAll(orgsToPurge);
					orgsToPurge.clear();
				}
			});
		}
		if (!orgsToPurge.isEmpty()) {
			theOrgRepo.deleteAll(orgsToPurge);
			orgsToPurge.clear();
		}
		purgeReport.append("\n\t").append(purged[0]).append(" Organizations purged");

		// Purge users who have not logged in for a while and who have no memberships
		purged[0] = 0;
		List<User> usersToPurge = new ArrayList<>();
		try (Stream<User> users = theUserRepo.getPurgeableUsers(unusedInactiveDate)) {
			users.forEach(user -> {
				purged[0]++;
				theNotificationSvc.publishMutation("user", false, ApiUser.of(user));
				usersToPurge.add(user);
				if (usersToPurge.size() > batchSize) {
					theUserRepo.deleteAll(usersToPurge);
					usersToPurge.clear();
				}
			});
		}
		if (!usersToPurge.isEmpty()) {
			theUserRepo.deleteAll(usersToPurge);
			usersToPurge.clear();
		}
		purgeReport.append("\n\t").append(purged[0]).append(" Users purged");

		QommonsUtils.printDuration(TimeUtils.between(now, Instant.now()), purgeReport.append("\n\tPurge completed in "), true);
		log.info(purgeReport.toString());
	}

	private void purgeMembers(List<Membership> members) {
		List<User> users = new ArrayList<>(members.size());
		for (Membership member : members) {
			theNotificationSvc.publishMutation("membership", false, ApiMembership.of(member));
			users.add(member.getMember());
		}
		Organization org = members.get(0).getOrganization();
		theHistoryRepo.deleteMultiWorkerHistory(org, users);
		theAssignmentRepo.deleteMultiWorkerAssignments(org, users);
		theMembersRepo.deleteAll(members);
		theEntityManager.flush();
	}
}
