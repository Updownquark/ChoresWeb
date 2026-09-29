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

import org.quark.misc.choresweb.api.ApiAssignment;
import org.quark.misc.choresweb.entities.Assignment;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.MembershipId;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.UserRepo;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

@Service
public class WorkService {
	private final JobRepo theJobRepo;
	private final AssignmentRepo theAssnRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final UserRepo theUserRepo;
	private final UserService theUserService;
	private final JobService theJobService;
	private final MembershipRepo theMembershipRepo;

	private final EntityChangeSet<BinaryId, OrgGroupedWork> theChanges = new EntityChangeSet<>(work -> work.id, 15000);

	WorkService(JobRepo jobRepo, AssignmentRepo assnRepo, PointChangeRecordRepo pointChangeRepo, UserRepo userRepo,
		UserService userService, JobService jobService, MembershipRepo membershipRepo) {
		theJobRepo = jobRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;
		theUserRepo = userRepo;
		theUserService = userService;
		theJobService = jobService;
		theMembershipRepo = membershipRepo;
	}

	@Transactional(readOnly = true)
	public List<Assignment> getAssignments(Membership me) {
		return theAssnRepo.getAssignments(me.getId().getOrganization());
	}

	@Transactional(readOnly = true)
	public EntityChangeSet.ChangeSet<ApiAssignment> getApiAssignments(Membership me) {
		return theChanges.getValues(() -> theAssnRepo.getAssignments(me.getId().getOrganization()).stream()//
			.map(ApiAssignment::of)//
			.toList());
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
		} else if (!me.isWorker() || me.getId().getMember().getId() != user.getId())
			throw new UnsupportedOperationException("You do not have permission to assign work to this user");

		Job job;
		try {
			job = theJobRepo.getReferenceById(jobId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");
		}
		if (job.getOrganization().getId() != me.getId().getOrganization().getId())
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");

		Assignment assn = theAssnRepo.getByJobAndWorker(job, user);
		boolean newAssn = assn == null;
		if (newAssn)
			assn = new Assignment(job, user);
		if (modify.test(assn) || newAssn) {
			if (assn.getCompleted() == 0 && assn.getNotes() == null) {
				if (!newAssn) {
					theAssnRepo.delete(assn);
					theChanges.changed(new OrgGroupedWork(job.getOrganization().getId(), ApiAssignment.deleted(userId, jobId)));
				}
				return null;
			}
			theAssnRepo.save(assn);
			theChanges.changed(new OrgGroupedWork(job.getOrganization().getId(), ApiAssignment.of(assn)));
		}
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
		} else if (!me.isWorker() || me.getId().getMember().getId() != user.getId())
			throw new UnsupportedOperationException("You do not have permission to delete work assignments for this user");

		Job job;
		try {
			job = theJobRepo.getReferenceById(userId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");
		}
		if (job.getOrganization().getId() != me.getId().getOrganization().getId())
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");

		theAssnRepo.deleteByJobAndWorker(job, user);
		theChanges.changed(new OrgGroupedWork(job.getOrganization().getId(), ApiAssignment.deleted(userId, jobId)));
	}

	@Transactional
	public void commitAssignments(Membership me) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to commit assignments for this organization");
		List<Assignment> assignments = theAssnRepo.getAssignments(me.getId().getOrganization());
		if (assignments.isEmpty())
			return;
		Instant now = Instant.now();
		Map<Long, Membership> members = new HashMap<>();
		Set<Job> jobs = new HashSet<>();
		List<PointChangeRecord> records = new ArrayList<>();
		for (Assignment assn : assignments) {
			if (assn.getCompleted() == 0 && (assn.getNotes() == null || assn.getNotes().isBlank()))
				continue;
			Membership member = members.computeIfAbsent(assn.getId().getWorker().getId(), _ -> {
				return theMembershipRepo.getReferenceById(new MembershipId(me.getId().getOrganization(), assn.getId().getWorker()));
			});
			if (member == null)
				continue;
			member.setPoints(member.getPoints() + assn.getCompleted());
			PointChangeRecord record = new PointChangeRecord(assn.getId().getJob(), member, now, assn.getCompleted());
			record.setNotes(assn.getNotes());
			records.add(record);
			theChanges.changed(new OrgGroupedWork(me.getId().getOrganization().getId(), ApiAssignment.of(assn)));
			if (jobs.add(assn.getId().getJob()))
				assn.getId().getJob().setLastDone(now);
		}
		thePointChangeRepo.saveAll(records);
		theMembershipRepo.saveAll(members.values());
		theJobRepo.saveAll(jobs);
		theAssnRepo.deleteAll(assignments);
		for (Membership member : members.values())
			theUserService.memberUpdated(member);
		for (Job job : jobs)
			theJobService.jobUpdated(job);
	}

	@Transactional
	public void clearAssignments(Membership me) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to commit assignments for this organization");
		List<Assignment> assignments = theAssnRepo.getAssignments(me.getId().getOrganization());
		if (assignments.isEmpty())
			return;
		theAssnRepo.deleteAll(assignments);
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
		} else if (!me.isWorker() || me.getId().getMember().getId() != user.getId())
			throw new UnsupportedOperationException("You do not have permission to record work for this user");

		Membership member;
		try {
			member = theMembershipRepo.getReferenceById(new MembershipId(me.getId().getOrganization(), user));
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("User with ID " + userId + " does not exist or is invisible");
		}

		Job job;
		try {
			job = theJobRepo.getReferenceById(userId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");
		}
		if (job.getOrganization().getId() != me.getId().getOrganization().getId())
			throw new NoSuchElementException("Job with ID " + jobId + " does not exist or is invisible");

		Instant now = Instant.now();
		PointChangeRecord record = new PointChangeRecord(job, member, now, points);
		if (notes != null && notes.isEmpty())
			notes = null;
		record.setNotes(notes);
		thePointChangeRepo.save(record);
		member.setLastActive(now);
		member.setPoints(record.getBeforePoints() + record.getPointChange());
		theMembershipRepo.save(member);
		theUserService.memberUpdated(member);
		job.setLastDone(now);
		theJobRepo.save(job);
		theJobService.jobUpdated(job);
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
		if (record.getOrganization().getId() != me.getId().getOrganization().getId())
			throw new NoSuchElementException("Point change record does not exist or is invisible");

		Membership member;
		try {
			member = theMembershipRepo.getReferenceById(new MembershipId(record.getOrganization(), record.getWorker()));
		} catch (EntityNotFoundException e) {
			thePointChangeRepo.delete(record);
			;
			return;
		}
		member.setPoints(member.getPoints() - record.getPointChange());
		theMembershipRepo.save(member);
		theUserService.memberUpdated(member);
		thePointChangeRepo.delete(record);
	}

	public void assignmentUpdated(Assignment assn) {
		theChanges.changed(new OrgGroupedWork(assn.getId().getJob().getOrganization().getId(), ApiAssignment.of(assn)));
	}

	public EntityChangeSet.ChangeSet<ApiAssignment> getChanges(long orgId, long lastKnownChange) {
		return theChanges.getChanges(lastKnownChange, assn -> assn.orgId == orgId, assn -> assn.work);
	}

	static class OrgGroupedWork {
		final long orgId;
		final BinaryId id;
		final ApiAssignment work;

		public OrgGroupedWork(long orgId, ApiAssignment work) {
			this.orgId = orgId;
			id = new BinaryId(work.userId(), work.jobId());
			this.work = work;
		}
	}
}
