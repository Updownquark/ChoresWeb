package org.quark.misc.choresweb.svc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiOrg;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.sync.SyncDataSource;
import org.quark.misc.choresweb.sync.SyncService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobService {
	private final JobRepo theJobRepo;
	private final OrganizationService theOrgService;
	private final EntityMutationNotificationService theNotificationSvc;

	JobService(JobRepo jobRepo, OrganizationService orgService, EntityMutationNotificationService notificationSvc,
		SyncService<User> syncService, ObjectMapper objectMapper) {
		theJobRepo = jobRepo;
		theOrgService = orgService;
		theNotificationSvc = notificationSvc;

		theNotificationSvc.installSerializer("job", ApiJob.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiJob.class, objectMapper));
		syncService.installDataSource(
			new SyncDataSource.SingleFieldValidationDataSource<User, ApiJob, Long>("job", ApiJob.class, objectMapper, "organization") {
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
						} else if (!event.isPresent() && event.getEntity() instanceof ApiOrg
							&& ((ApiOrg) event.getEntity()).id() == orgId) {
							return ValidationChange.RevokeSubscription;
						}
						return ValidationChange.Ignore;// Still valid
					};
				}

				@Override
				protected Stream<ApiJob> getEntities(Long authFieldValue) {
					return theJobRepo.getByOrganizationId(authFieldValue).stream().map(ApiJob::of);
				}
			});
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
		theNotificationSvc.publishMutation("job", true, ApiJob.of(job));
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
		theNotificationSvc.publishMutation("job", true, ApiJob.of(job));
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
		theNotificationSvc.publishMutation("job", false, ApiJob.of(job));
	}

	public void jobUpdated(Job job) {
		theNotificationSvc.publishMutation("job", true, ApiJob.of(job));
	}
}
