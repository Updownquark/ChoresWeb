package org.quark.misc.choresweb.svc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

@Service
public class JobService {
	private final JobRepo theJobRepo;
	private final EntityChangeSet<Long, OrgGroupedJob> theChanges = new EntityChangeSet<>(job -> job.job.id(), 15000);

	JobService(JobRepo jobRepo) {
		theJobRepo = jobRepo;
	}

	@Transactional(readOnly = true)
	public List<Job> getJobs(Membership me) {
		return theJobRepo.getOrgJobs(me.getId().getOrganization());
	}

	@Transactional(readOnly = true)
	public EntityChangeSet.ChangeSet<ApiJob> getApiJobs(Membership me) {
		return theChanges.getValues(() -> theJobRepo.getOrgJobs(me.getId().getOrganization()).stream()//
			.map(ApiJob::of)//
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
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ApiJob.of(job)));
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
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ApiJob.of(job)));
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
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ApiJob.deleted(jobId)));
	}

	public void jobUpdated(Job job) {
		theChanges.changed(new OrgGroupedJob(job.getOrganization().getId(), ApiJob.of(job)));
	}

	public EntityChangeSet.ChangeSet<ApiJob> getChanges(long orgId, long lastKnownChange) {
		return theChanges.getChanges(lastKnownChange, job -> job.orgId == orgId, job -> job.job);
	}

	static class OrgGroupedJob {
		final long orgId;
		final ApiJob job;

		OrgGroupedJob(long orgId, ApiJob job) {
			this.orgId = orgId;
			this.job = job;
		}
	}
}
