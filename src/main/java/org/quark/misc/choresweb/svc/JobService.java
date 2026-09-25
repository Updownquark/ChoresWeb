package org.quark.misc.choresweb.svc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ProtoJob;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

@Service
public class JobService {
	private final JobRepo theJobRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final EntityChangeSet<Long, OrgGroupedJob> theChanges = new EntityChangeSet<>(job -> job.job.id(), 15000);

	JobService(JobRepo jobRepo, PointChangeRecordRepo pointChangeRepo) {
		theJobRepo = jobRepo;
		thePointChangeRepo = pointChangeRepo;
	}

	@Transactional(readOnly = true)
	public List<Job> getJobs(Membership me) {
		return theJobRepo.getOrgJobs(me.getId().getOrganization());
	}

	@Transactional(readOnly = true)
	public EntityChangeSet.ChangeSet<ProtoJob> getApiJobs(Membership me) {
		return theChanges.getValues(() -> theJobRepo.getOrgJobs(me.getId().getOrganization()).stream()//
			.map(ProtoJob::of)//
			.toList());
	}

	@Transactional(readOnly = true)
	public Job getById(Membership me, long jobId) {
		Job job;
		try {
			job = theJobRepo.getReferenceById(jobId);
		} catch (EntityNotFoundException e) {
			return null;
		}
		if (job == null || (me != null && job.getOrganization().getId() != me.getId().getOrganization().getId()))
			return null;
		return job;
	}

	@Transactional(readOnly = true)
	public boolean hasJobNamed(long orgId, String name) {
		return theJobRepo.existsByOrganizationIdAndName(orgId, name);
	}

	@Transactional
	public Job createJob(Membership me, Consumer<Job> configure) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to add jobs to this organization");
		String name = StringUtils.getNewItemName(n -> theJobRepo.getByName(n) > 0, "A Job", StringUtils.SIMPLE_DUPLICATES);
		Job job = new Job(me.getId().getOrganization(), name);
		if (configure != null)
			configure.accept(job);
		theJobRepo.save(job);
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ProtoJob.of(job)));
		return job;
	}

	@Transactional
	public Job modifyJob(Membership me, long jobId, Predicate<Job> modify) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify resources in this organization");
		Job job;
		try {
			job = theJobRepo.getReferenceById(jobId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException();
		}
		if (job == null || job.getOrganization().getId() != me.getId().getOrganization().getId())
			throw new NoSuchElementException();
		if (modify.test(job))
			theJobRepo.save(job);
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ProtoJob.of(job)));
		return job;
	}

	@Transactional
	public void deleteJob(Membership me, long jobId) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to remove resources from this organization");
		Job job;
		try {
			job = theJobRepo.getReferenceById(jobId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException();
		}
		if (job == null || job.getOrganization().getId() != me.getId().getOrganization().getId())
			throw new NoSuchElementException();
		theJobRepo.delete(job);
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ProtoJob.deleted(jobId)));
	}

	@Transactional(readOnly = true)
	public List<PointChangeRecord.FullPcrDto> getJobHistory(Membership me, Job job, int pageNumber, int pageSize) {
		if (me.getId().getOrganization().getId() != job.getOrganization().getId())
			throw new UnsupportedOperationException("You must sign in to the organization you want to view");
		return thePointChangeRepo.getJobHistory(job, PageRequest.of(pageNumber, pageSize)).getContent();
	}

	public void jobUpdated(Job job) {
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ProtoJob.of(job)));
	}

	public EntityChangeSet.ChangeSet<ProtoJob> getChanges(long orgId, long lastKnownChange) {
		return theChanges.getChanges(lastKnownChange, job -> job.orgId == orgId, job -> job.job);
	}

	static class OrgGroupedJob {
		final long orgId;
		final ProtoJob job;

		OrgGroupedJob(long orgId, ProtoJob job) {
			this.orgId = orgId;
			this.job = job;
		}
	}
}
