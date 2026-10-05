package org.quark.misc.choresweb.svc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.api.ChoresApplicationEvent;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.repos.JobRepo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

@Service
public class JobService {
	private final JobRepo theJobRepo;
	private final OrganizationService theOrgService;
	private final ApplicationEventPublisher theEventPublisher;

	JobService(JobRepo jobRepo, OrganizationService orgService, ApplicationEventPublisher eventPublisher) {
		theJobRepo = jobRepo;
		theOrgService = orgService;
		theEventPublisher = eventPublisher;
		System.out.println("Initializing JobService");
	}

	@Transactional(readOnly = true)
	public List<Job> getJobs(Membership me) {
		return theJobRepo.getOrgJobs(me.getOrganization());
	}

	@Transactional(readOnly = true)
	public List<ApiJob> getApiJobs(Membership me) {
		List<ApiJob> jobs = theJobRepo.getOrgJobs(me.getOrganization()).stream()//
			.map(ApiJob::of)//
			.toList();
		return jobs;
	}

	@Transactional(readOnly = true)
	public Job getById(Jwt me, long jobId) {
		Job job;
		try {
			job = theJobRepo.findById(jobId).orElse(null);
		} catch (EntityNotFoundException e) {
			return null;
		}
		if (job == null)
			return null;
		theOrgService.getMe(me, job.getOrganization().getId());
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
		Job job = new Job(me.getOrganization(), name);
		if (configure != null)
			configure.accept(job);
		theJobRepo.save(job);
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "job", job.getId(), true));
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
		if (job == null || job.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException();
		if (modify.test(job))
			theJobRepo.save(job);
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "job", job.getId(), true));
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
		if (job == null || job.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException();
		theJobRepo.delete(job);
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, me.getOrganization().getId(), "job", job.getId(), false));
	}

	public void jobUpdated(Job job) {
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, job.getOrganization().getId(), "job", job.getId(), true));
	}
}
