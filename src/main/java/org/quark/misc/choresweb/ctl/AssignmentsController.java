package org.quark.misc.choresweb.ctl;

import java.util.Collections;
import java.util.List;

import org.quark.misc.choresweb.api.ApiAssignment;
import org.quark.misc.choresweb.entities.Assignment;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.JobService;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.WorkService;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentsController {
	private final OrganizationService theMembershipSvc;
	private final WorkService theWorkService;

	public AssignmentsController(OrganizationService membershipSvc, WorkService workService, JobService jobService) {
		theMembershipSvc = membershipSvc;
		theWorkService = workService;
		System.out.println("Testing utils: " + ChoresWebUtils.getNewName(Collections.emptyList(), 100, "A Thing"));
	}

	@GetMapping("/by-org/{orgId}")
	public List<ApiAssignment> getAssignments(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId) {
		return theWorkService.getApiAssignments(theMembershipSvc.getMe(user, orgId));
	}

	@PutMapping
	public ApiAssignment addOrModifyAssignment(@AuthenticationPrincipal Jwt user, @RequestBody AssnAddOrMod action) {
		Membership me = theMembershipSvc.getMe(user, action.orgId());
		Assignment assn = theWorkService.assign(me, action.jobId(), action.userId(), modAssn -> {
			if (action.completed() != null)
				modAssn.setCompleted(action.completed());
			if (action.notes() != null)
				modAssn.setNotes(action.notes());
			return true;
		});
		return ApiAssignment.of(assn);
	}

	@DeleteMapping
	public void deleteAssignment(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId,
		@RequestParam(required = true) long userId, @RequestParam(required = true) long jobId) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		theWorkService.deleteAssignment(membership, jobId, userId);
	}

	@DeleteMapping("/all")
	public void clearAssignments(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		theWorkService.clearAssignments(membership);
	}

	@PostMapping("/submit")
	public void submitAssignments(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		theWorkService.commitAssignments(membership);
	}

	@PostMapping("/report")
	public void reportWork(@AuthenticationPrincipal Jwt user, @RequestBody(required = true) AssnAddOrMod work) {
		if (work.completed == null)
			throw new IllegalArgumentException("Completed is required for a work report");
		Membership membership = theMembershipSvc.getMe(user, work.orgId);
		theWorkService.recordWork(membership, work.jobId, work.userId, work.completed, work.notes());
	}

	static record AssnAddOrMod(long orgId, long userId, long jobId, Integer completed, String notes) {
		public long orgId() {
			return orgId;
		}

		public long userId() {
			return userId;
		}

		public long jobId() {
			return jobId;
		}

		public Integer completed() {
			return completed;
		}

		public String notes() {
			return notes;
		}
	}
}
