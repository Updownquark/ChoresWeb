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

import org.apache.commons.lang3.StringUtils;
import org.quark.misc.choresweb.api.ApiAssignment;
import org.quark.misc.choresweb.api.ChoresApplicationEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher theEventPublisher;

	WorkService(JobRepo jobRepo, AssignmentRepo assnRepo, PointChangeRecordRepo pointChangeRepo, UserRepo userRepo, UserService userService,
		JobService jobService, MembershipRepo membershipRepo, ApplicationEventPublisher eventPublisher) {
		theJobRepo = jobRepo;
		theAssnRepo = assnRepo;
		thePointChangeRepo = pointChangeRepo;
		theUserRepo = userRepo;
		theUserService = userService;
		theJobService = jobService;
		theMembershipRepo = membershipRepo;
		theEventPublisher = eventPublisher;
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
					theEventPublisher.publishEvent(
						ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "assignment", assn.getId(), false));
				}
				return null;
			}
			theAssnRepo.save(assn);
			theEventPublisher
				.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "assignment", assn.getId(), true));
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
		theEventPublisher
			.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "assignment", assn.getId(), false));
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
			member.setPoints(member.getPoints() + assn.getCompleted());
			PointChangeRecord record = new PointChangeRecord(assn.getJob(), member, now, assn.getCompleted());
			record.setNotes(assn.getNotes());
			records.add(record);
			theEventPublisher
				.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "assignment", assn.getId(), false));
			if (jobs.add(assn.getJob()))
				assn.getJob().setLastDone(now);
		}
		thePointChangeRepo.saveAll(records);
		theMembershipRepo.saveAll(members.values());
		theJobRepo.saveAll(jobs);
		theAssnRepo.deleteAll(assignments);
		for (Membership member : members.values())
			theUserService.memberUpdated(member);
		for (Job job : jobs)
			theJobService.jobUpdated(job);
		for (PointChangeRecord record : records)
			theEventPublisher
				.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "history", record.getId(), true));
	}

	@Transactional
	public void clearAssignments(Membership me) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to commit assignments for this organization");
		List<Assignment> assignments = theAssnRepo.getAssignments(me.getOrganization());
		if (assignments.isEmpty())
			return;
		for (Assignment assn : assignments)
			theEventPublisher
				.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "assignment", assn.getId(), false));
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
		theEventPublisher
			.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "history", record.getId(), true));
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
			;
			return;
		}
		member.setPoints(member.getPoints() - record.getPointChange());
		theMembershipRepo.save(member);
		theUserService.memberUpdated(member);
		thePointChangeRepo.delete(record);
		theEventPublisher
			.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "history", record.getId(), false));
	}

	public void assignmentUpdated(Assignment assn) {
		theEventPublisher.publishEvent(
			ChoresApplicationEvent.dataChange(this, assn.getJob().getOrganization().getId(), "assignment", assn.getId(), true));
	}
}
