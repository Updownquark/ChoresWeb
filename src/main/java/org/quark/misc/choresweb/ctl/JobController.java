package org.quark.misc.choresweb.ctl;

import java.util.Collections;
import java.util.Set;

import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.JobService;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
	private final OrganizationService theMembershipSvc;
	private final JobService theJobService;

	public JobController(OrganizationService membershipSvc, JobService jobService) {
		theMembershipSvc = membershipSvc;
		theJobService = jobService;
	}

	@GetMapping("/by-org/{orgId}")
	public EntityChangeSet.ChangeSet<ApiJob> getJobs(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId) {
		System.out.println("Getting " + orgId + " jobs");
		return theJobService.getApiJobs(theMembershipSvc.getMe(user, orgId));
	}

	@GetMapping("/{id}")
	public ApiJob getJob(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId, @PathVariable("orgId") long id) {
		return ApiJob.of(theJobService.getById(theMembershipSvc.getMe(user, orgId), id));
	}

	@GetMapping("/changes/{orgId}")
	public EntityChangeSet.ChangeSet<ApiJob> getJobChanges(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId,
		@RequestParam(required = true) long lastKnownChange) {
		theMembershipSvc.getMe(user, orgId); // Ensure the user has access
		return theJobService.getChanges(orgId, lastKnownChange);
	}

	@PutMapping
	public EntityChangeSet.ChangeSet<ApiJob> addOrModifyJob(@AuthenticationPrincipal Jwt user, //
		@RequestBody JobAddOrMod action, //
		@RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getMe(user, action.orgId());
		if (action.jobId != null) { // Modify a job
			theJobService.modifyJob(membership, action.jobId, job -> {
				boolean mod = false;
				boolean withName = action.name != null && !action.name.equals(job.getName());
				if (withName) {
					if (action.name.length() == 0)
						throw new IllegalArgumentException("Name cannot be empty");
					else if (action.name.length() > 60)
						throw new IllegalArgumentException("Name cannot exceed 60 characters");
					else if (theJobService.hasJobNamed(action.orgId(), action.name))
						throw new IllegalArgumentException("Another job named '" + action.name + "' exists");
					mod = true;
				}
				boolean withValue = action.value() != null;
				if (withValue) {
					if (action.value < 0)
						throw new IllegalArgumentException("Value (points) cannot be negative");
					else if (action.value > 1000)
						throw new IllegalArgumentException("Value (pointS) cannot exceed 1000");
					mod = true;
				}
				if (withName)
					job.setName(action.name());
				if (withValue)
					job.setValue(action.value());
				if (action.minLevel() != null) {
					mod = true;
					job.setMinLevel(action.minLevel());
				}
				if (action.maxLevel() != null) {
					mod = true;
					job.setMaxLevel(action.maxLevel());
				}
				if (action.inclusionLabels() != null) {
					mod = true;
					job.setInclusionLabels(String.join(",", action.inclusionLabels()));
				}
				if (action.exclusionLabels() != null) {
					mod = true;
					job.setExclusionLabels(String.join(",", action.exclusionLabels()));
				}
				if (action.priority() != null) {
					mod = true;
					job.setPriority(action.priority());
				}
				if (action.active() != null) {
					mod = true;
					job.setActive(action.active());
				}
				return mod;
			});
		} else { // Add a job
			String newName = ChoresWebUtils.getNewName(theJobService.getJobs(membership), 60, //
				action.name(), "A Job"); // So the new job is at the top, easy to find
			theJobService.createJob(membership, newJob -> {
				newJob.setName(newName);
				if (action.active() != null)
					newJob.setActive(action.active());
				else
					newJob.setActive(true);
				if (action.exclusionLabels() != null)
					newJob.setExclusionLabels(String.join(",", action.exclusionLabels()));
				if (action.inclusionLabels() != null)
					newJob.setInclusionLabels(String.join(",", action.inclusionLabels()));
				if (action.minLevel() != null)
					newJob.setMinLevel(action.minLevel());
				if (action.maxLevel() != null)
					newJob.setMaxLevel(action.maxLevel());
				else
					newJob.setMaxLevel(Integer.MAX_VALUE);
				if (action.priority() != null)
					newJob.setPriority(action.priority());
				if (action.value() != null)
					newJob.setValue(action.value());
			});
		}
		return theJobService.getChanges(action.orgId(), lastKnownChange);
	}

	@DeleteMapping("/{id}")
	public EntityChangeSet.ChangeSet<ApiJob> deleteJob(@AuthenticationPrincipal Jwt user, long id,
		@RequestParam(required = true) long lastKnownChange) {
		Job job = theJobService.getById(null, id);
		if (job == null)
			return new EntityChangeSet.ChangeSet<>(lastKnownChange, Collections.emptyList());
		Membership membership = theMembershipSvc.getMe(user, job.getOrganization().getId());
		theJobService.deleteJob(membership, id);
		return theJobService.getChanges(job.getOrganization().getId(), lastKnownChange);
	}

	public static record JobAddOrMod(long orgId, Long jobId, String name, Integer value, Integer minLevel, Integer maxLevel,
		Set<String> inclusionLabels, Set<String> exclusionLabels, Integer priority, Boolean active) {

		public long orgId() {
			return orgId;
		}

		public Long jobId() {
			return jobId;
		}

		public String name() {
			return name;
		}

		public Integer value() {
			return value;
		}

		public Integer minLevel() {
			return minLevel;
		}

		public Integer maxLevel() {
			return maxLevel;
		}

		public Set<String> inclusionLabels() {
			return inclusionLabels;
		}

		public Set<String> exclusionLabels() {
			return exclusionLabels;
		}

		public Integer priority() {
			return priority;
		}

		public Boolean active() {
			return active;
		}
	}
}
