package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.qommons.TimeUtils;
import org.quark.misc.choresweb.api.ApiAssignment;
import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiOrg;
import org.quark.misc.choresweb.entities.Assignment;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.sync.SyncDataSource;
import org.quark.misc.choresweb.sync.SyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkService {
	private final JobRepo theJobRepo;
	private final AssignmentRepo theAssnRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final UserRepo theUserRepo;
	private final UserService theUserService;
	private final OrganizationService theOrgService;
	private final JobService theJobService;
	private final MembershipRepo theMembershipRepo;
	private final EntityMutationNotificationService theNotificationSvc;

	WorkService(JobRepo jobRepo, AssignmentRepo assnRepo, PointChangeRecordRepo pointChangeRepo, UserRepo userRepo, UserService userService,
		OrganizationService orgService, JobService jobService, MembershipRepo membershipRepo,
		EntityMutationNotificationService notificationSvc, SyncService<User> syncService, ObjectMapper objectMapper) {
		theJobRepo = jobRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;
		theUserRepo = userRepo;
		theUserService = userService;
		theOrgService = orgService;
		theJobService = jobService;
		theMembershipRepo = membershipRepo;
		theNotificationSvc = notificationSvc;

		theNotificationSvc.installSerializer("assignment", ApiAssignment.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiAssignment.class, objectMapper));
		syncService.installDataSource(new SyncDataSource.SingleFieldValidationDataSource<User, ApiAssignment, Long>("assignment",
			ApiAssignment.class, objectMapper, "organization") {
			@Override
			protected String isAuthorized(User user, Long authFieldValue) {
				Membership me = theOrgService.getMembership(user, authFieldValue);
				if (me == null)
					return "No such organization with ID " + authFieldValue + " or you are not a member of it";
				return null;
			}

			@Override
			protected ValidationMaintainer<User> maintainValidation(User user, Long authFieldValue) {
				long userId = user.getId(); // Release the user object to garbage collection
				long orgId = authFieldValue.longValue();
				return event -> {
					if (!event.isPresent() && event.getEntity() instanceof ApiMembership membership) {
						if (membership.member().id() == userId && membership.organization().id() == orgId) {
							// The user's membership in the organization has been revoked
							return ValidationChange.RevokeSubscription;
						}
					} else if (!event.isPresent() && event.getEntity() instanceof ApiOrg && ((ApiOrg) event.getEntity()).id() == orgId) {
						return ValidationChange.RevokeSubscription;
					}
					return ValidationChange.Ignore;// Still valid
				};
			}

			@Override
			protected Stream<ApiAssignment> getEntities(Long authFieldValue) {
				return theAssnRepo.getByOrganizationId(authFieldValue).stream().map(ApiAssignment::of);
			}
		});
	}

	@Transactional(readOnly = true)
	public List<Assignment> getAssignments(Membership me) {
		return theAssnRepo.getAssignments(me.getOrganization());
	}

	@Transactional(readOnly = true)
	public List<ApiAssignment> getApiAssignments(Membership me) {
		return theAssnRepo.getAssignments(me.getOrganization()).stream()//
			.map(ApiAssignment::of)//
			.toList();
	}

	@Transactional
	public Assignment assign(Membership me, long jobId, long userId, Predicate<Assignment> modify) {
		User user;
		try {
			user = theUserRepo.getReferenceById(userId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("User with ID " + userId + " does not exist or is invisible");
		}
		if (me.isManager()) { // All good
		} else if (!me.isWorker() || me.getMember().getId() != user.getId())
			throw new UnsupportedOperationException("You do not have permission to assign work to this user");

		Job job;
		try {
			job = theJobRepo.getReferenceById(jobId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");
		}
		if (job.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");

		Assignment assn = theAssnRepo.getByJobAndWorker(job, user);
		boolean newAssn = assn == null;
		if (newAssn)
			assn = new Assignment(job, user);
		if (modify.test(assn) || newAssn) {
			if (assn.getCompleted() == 0 && assn.getNotes() == null) {
				if (!newAssn) {
					theAssnRepo.delete(assn);
					theNotificationSvc.publishMutation("assignment", false, ApiAssignment.of(assn));
				}
				return null;
			}
			theAssnRepo.save(assn);
			theNotificationSvc.publishMutation("assignment", true, ApiAssignment.of(assn));
		}
		theUserService.updateOrg(me.getOrganization());
		return assn;
	}

	@Transactional
	public void deleteAssignment(Membership me, long jobId, long userId) {
		User user;
		try {
			user = theUserRepo.getReferenceById(userId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("User with ID " + userId + " does not exist or is invisible");
		}
		if (me.isManager()) { // All good
		} else if (!me.isWorker() || me.getMember().getId() != user.getId())
			throw new UnsupportedOperationException("You do not have permission to delete work assignments for this user");

		Job job;
		try {
			job = theJobRepo.getReferenceById(userId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");
		}
		if (job.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");

		Assignment assn = theAssnRepo.getByJobAndWorker(job, user);
		if (assn == null)
			return;
		theAssnRepo.delete(assn);
		theNotificationSvc.publishMutation("assignment", false, ApiAssignment.of(assn));
		theUserService.updateOrg(me.getOrganization());
	}

	@Transactional
	public void commitAssignments(Membership me) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to commit assignments for this organization");
		List<Assignment> assignments = theAssnRepo.getAssignments(me.getOrganization());
		if (assignments.isEmpty())
			return;
		Instant now = Instant.now();
		Map<Long, Membership> members = new HashMap<>();
		Set<Job> jobs = new HashSet<>();
		List<PointChangeRecord> records = new ArrayList<>();
		for (Assignment assn : assignments) {
			if (assn.getCompleted() == 0 && (assn.getNotes() == null || assn.getNotes().isBlank()))
				continue;
			Membership member = members.computeIfAbsent(assn.getWorker().getId(), _ -> {
				return theMembershipRepo.getMembership(assn.getWorker().getId(), me.getOrganization().getId());
			});
			if (member == null)
				continue;
			PointChangeRecord record = new PointChangeRecord(assn.getJob(), member, now, assn.getCompleted());
			member.setPoints(member.getPoints() + assn.getCompleted());
			record.setNotes(assn.getNotes());
			records.add(record);
			theNotificationSvc.publishMutation("assignment", false, ApiAssignment.of(assn));
		}
		thePointChangeRepo.saveAll(records);
		theMembershipRepo.saveAll(members.values());
		theJobRepo.saveAll(jobs);
		theAssnRepo.deleteAll(assignments);
		for (Membership member : members.values()) {
			member.setLastActive(now);
			theUserService.memberUpdated(member);
		}
		for (Job job : jobs) {
			job.setLastDone(now);
			theJobService.jobUpdated(job);
		}
		for (PointChangeRecord record : records)
			theNotificationSvc.publishMutation("history", true, PointChangeRecord.FullPcrDto.of(record));
		theUserService.updateOrg(me.getOrganization());
	}

	@Transactional
	public void clearAssignments(Membership me) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to commit assignments for this organization");
		List<Assignment> assignments = theAssnRepo.getAssignments(me.getOrganization());
		if (assignments.isEmpty())
			return;
		for (Assignment assn : assignments)
			theNotificationSvc.publishMutation("assignment", false, ApiAssignment.of(assn));
		theAssnRepo.deleteAll(assignments);
		theUserService.updateOrg(me.getOrganization());
	}

	@Transactional
	public PointChangeRecord recordWork(Membership me, long jobId, long userId, int points, String notes) {
		User user;
		try {
			user = theUserRepo.getReferenceById(userId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("User with ID " + userId + " does not exist or is invisible");
		}
		if (me.isManager()) { // All good
		} else if (!me.isWorker() || me.getMember().getId() != user.getId())
			throw new UnsupportedOperationException("You do not have permission to record work for this user");

		Membership member;
		try {
			member = theMembershipRepo.getMembership(user.getId(), me.getOrganization().getId());
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("User with ID " + userId + " does not exist or is invisible");
		}

		Job job;
		try {
			job = theJobRepo.getReferenceById(jobId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");
		}
		if (job.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");

		Instant now = Instant.now();
		PointChangeRecord record = new PointChangeRecord(job, member, now, points);
		if (notes != null) {
			if (notes.isEmpty())
				notes = null;
			else if (notes.length() > 100)
				notes = StringUtils.abbreviate(notes, 100);
		}
		record.setNotes(notes);
		thePointChangeRepo.save(record);
		member.setLastActive(now);
		member.setPoints(record.getBeforePoints() + record.getPointChange());
		theMembershipRepo.save(member);
		theUserService.memberUpdated(member);
		job.setLastDone(now);
		theJobRepo.save(job);
		theJobService.jobUpdated(job);
		theNotificationSvc.publishMutation("history", true, PointChangeRecord.FullPcrDto.of(record));
		theUserService.updateOrg(me.getOrganization());
		return record;
	}

	@Transactional
	public void undo(Membership me, long pointChangeRecordId) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to manage point history for this organization");
		PointChangeRecord record;
		try {
			record = thePointChangeRepo.getReferenceById(pointChangeRecordId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Point change record does not exist or is invisible");
		}
		if (record.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException("Point change record does not exist or is invisible");

		Membership member;
		try {
			member = theMembershipRepo.getMembership(record.getWorker().getId(), record.getOrganization().getId());
		} catch (EntityNotFoundException e) {
			thePointChangeRepo.delete(record);
			return;
		}
		member.setPoints(member.getPoints() - record.getPointChange());
		theMembershipRepo.save(member);
		theUserService.memberUpdated(member);
		thePointChangeRepo.delete(record);
		theNotificationSvc.publishMutation("history", false, PointChangeRecord.FullPcrDto.of(record));
		theUserService.updateOrg(me.getOrganization());

		Membership worker = theMembershipRepo.getByMemberAndOrganizationId(record.getWorker(), record.getOrganization().getId());
		if (worker != null && (worker.getLastActive() == null //
			|| Math.abs(TimeUtils.between(worker.getLastActive(), record.getTime()).getSeconds()) <= 1)) {
			Instant lastActive = thePointChangeRepo.getLastActive(me.getOrganization(), worker.getId());
			boolean updated = false;
			if (lastActive == null && worker.getLastActive() != null) {
				updated = true;
				worker.setLastActive(lastActive);
			} else if (lastActive != null
				&& (worker.getLastActive() == null || Math.abs(TimeUtils.between(lastActive, worker.getLastActive()).getSeconds()) > 10)) {
				updated = true;
				worker.setLastActive(lastActive);
			}
			if (updated) {
				theMembershipRepo.save(worker);
				theNotificationSvc.publishMutation("membership", true, ApiMembership.of(worker));
			}
			record.getWorker().setLastActive(lastActive);
		}

		Job job = record.getChangeType() == PointChangeRecord.PointChangeType.Job
			? theJobRepo.findById(record.getChangeSourceId()).orElse(null) : null;
		if (job != null && (job.getLastDone() == null //
			|| Math.abs(TimeUtils.between(job.getLastDone(), record.getTime()).getSeconds()) <= 1)) {
			Instant lastActive = thePointChangeRepo.getLastDone(me.getOrganization(), job.getId());
			boolean updated = false;
			if (lastActive == null && job.getLastDone() != null) {
				updated = true;
				job.setLastDone(lastActive);
			} else if (lastActive != null
				&& (job.getLastDone() == null || Math.abs(TimeUtils.between(lastActive, job.getLastDone()).getSeconds()) > 10)) {
				updated = true;
				job.setLastDone(lastActive);
			}
			if (updated) {
				theJobRepo.save(job);
				theNotificationSvc.publishMutation("job", true, ApiJob.of(job));
			}
			record.getWorker().setLastActive(lastActive);
		}
	}

	public void assignmentUpdated(Assignment assn) {
		theNotificationSvc.publishMutation("assignment", true, ApiAssignment.of(assn));
	}
}
