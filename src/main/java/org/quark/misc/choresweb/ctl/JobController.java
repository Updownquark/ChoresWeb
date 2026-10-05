package org.quark.misc.choresweb.ctl;

import java.util.List;
import java.util.Set;

import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.JobService;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
	public List<ApiJob> getJobs(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId) {
		return theJobService.getApiJobs(theMembershipSvc.getMe(user, orgId));
	}

	@GetMapping("/{id}")
	public ApiJob getJob(@AuthenticationPrincipal Jwt user, @PathVariable("id") long id) {
		return ApiJob.of(theJobService.getById(user, id));
	}

	@PutMapping
	public ApiJob addOrModifyJob(@AuthenticationPrincipal Jwt user, @RequestBody JobAddOrMod action) {
		Membership membership = theMembershipSvc.getMe(user, action.orgId());
		Job job;
		if (action.jobId != null) { // Modify a job
			job = theJobService.modifyJob(membership, action.jobId, modJob -> {
				boolean mod = false;
				boolean withName = action.name != null && !action.name.equals(modJob.getName());
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
					modJob.setName(action.name());
				if (withValue)
					modJob.setValue(action.value());
				if (action.minLevel() != null) {
					mod = true;
					modJob.setMinLevel(action.minLevel());
				}
				if (action.maxLevel() != null) {
					mod = true;
					modJob.setMaxLevel(action.maxLevel());
				}
				if (action.inclusionLabels() != null) {
					mod = true;
					modJob.setInclusionLabels(String.join(",", action.inclusionLabels()));
				}
				if (action.exclusionLabels() != null) {
					mod = true;
					modJob.setExclusionLabels(String.join(",", action.exclusionLabels()));
				}
				if (action.priority() != null) {
					mod = true;
					modJob.setPriority(action.priority());
				}
				if (action.active() != null) {
					mod = true;
					modJob.setActive(action.active());
				}
				return mod;
			});
		} else { // Add a job
			String newName = ChoresWebUtils.getNewName(theJobService.getJobs(membership), 60, //
				action.name(), "A Job"); // So the new job is at the top, easy to find
			job = theJobService.createJob(membership, newJob -> {
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
					newJob.setMaxLevel(100);
				if (action.priority() != null)
					newJob.setPriority(action.priority());
				if (action.value() != null)
					newJob.setValue(action.value());
			});
		}
		return ApiJob.of(job);
	}

	@DeleteMapping("/{id}")
	public void deleteJob(@AuthenticationPrincipal Jwt user, @PathVariable("id") long id) {
		Job job = theJobService.getById(user, id);
		if (job == null)
			return;
		Membership membership = theMembershipSvc.getMe(user, job.getOrganization().getId());
		if (!membership.isManager())
			throw new UnsupportedOperationException("You do not have permission to delete jobs in this organization");
		theJobService.deleteJob(membership, id);
	}

	public static record JobAddOrMod(long orgId, Long jobId, String name, Integer value, Integer minLevel, Integer maxLevel,
		Set<String> inclusionLabels, //
		Set<String> exclusionLabels, //
		Integer priority, Boolean active) {}
}
