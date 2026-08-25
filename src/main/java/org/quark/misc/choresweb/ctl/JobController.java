package org.quark.misc.choresweb.ctl;

import java.util.List;

import org.quark.misc.choresweb.svc.JobService;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
	public List<ProtoJob> getJobs(@AuthenticationPrincipal Jwt user, long organizationId) {
		return theJobService.getJobs(theMembershipSvc.getOrganization(user.getClaimAsString("email"), organizationId)).stream()//
			.map(ProtoJob::of)//
			.toList();
	}

	@GetMapping("/{id}")
	public ProtoJob getJob(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId, long jobId) {
		return ProtoJob.of(theJobService.getById(theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId), jobId));
	}
}
