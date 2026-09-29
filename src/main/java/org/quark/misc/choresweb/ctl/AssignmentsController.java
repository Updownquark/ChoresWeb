package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.api.ApiAssignment;
import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.UserService;
import org.quark.misc.choresweb.svc.WorkService;
import org.quark.misc.choresweb.util.EntityChangeSet;
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
	private final UserService theUserService;

	public AssignmentsController(OrganizationService membershipSvc, WorkService workService, UserService userService) {
		theMembershipSvc = membershipSvc;
		theWorkService = workService;
		theUserService = userService;
	}

	@GetMapping("/by-org/{orgId}")
	public EntityChangeSet.ChangeSet<ApiAssignment> getAssignments(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId) {
		return theWorkService.getApiAssignments(theMembershipSvc.getMe(user, orgId));
	}

	@GetMapping("/changes/{orgId}")
	public EntityChangeSet.ChangeSet<ApiAssignment> getAssignmentChanges(@AuthenticationPrincipal Jwt user,
		@PathVariable("orgId") long orgId, @RequestParam(required = true) long lastKnownChange) {
		theMembershipSvc.getMe(user, orgId); // Ensure the user has access
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@PutMapping
	public EntityChangeSet.ChangeSet<ApiAssignment> addOrModifyAssignment(@AuthenticationPrincipal Jwt user,
		@RequestBody AssnAddOrMod action, @RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getMe(user, action.orgId());
		theWorkService.assign(me, action.jobId(), action.userId(), assn -> {
			if (action.completed() != null)
				assn.setCompleted(action.completed());
			if (action.notes() != null)
				assn.setNotes(action.notes());
			return true;
		});
		return theWorkService.getChanges(action.orgId(), lastKnownChange);
	}

	@DeleteMapping
	public EntityChangeSet.ChangeSet<ApiAssignment> deleteAssignment(@AuthenticationPrincipal Jwt user,
		@RequestParam(required = true) long orgId, @RequestParam(required = true) long userId, @RequestParam(required = true) long jobId,
		@RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		theWorkService.deleteAssignment(membership, jobId, userId);
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@DeleteMapping("/all")
	public EntityChangeSet.ChangeSet<ApiAssignment> deleteAssignment(@AuthenticationPrincipal Jwt user,
		@RequestParam(required = true) long orgId, @RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		theWorkService.clearAssignments(membership);
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@PostMapping("/submit")
	public EntityChangeSet.ChangeSet<ApiAssignment> submitAssignments(@AuthenticationPrincipal Jwt user,
		@RequestParam(required = true) long orgId, @RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		theWorkService.commitAssignments(membership);
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@PostMapping("/report")
	public EntityChangeSet.ChangeSet<ApiMembership> reportWork(@AuthenticationPrincipal Jwt user,
		@RequestBody(required = true) AssnAddOrMod work, @RequestParam(required = true) long lastKnownChange) {
		if (work.completed == null)
			throw new IllegalArgumentException("Completed is required for a work report");
		Membership membership = theMembershipSvc.getMe(user, work.orgId);
		theWorkService.recordWork(membership, work.jobId, work.userId, work.completed, work.notes());
		return theUserService.getChanges(work.orgId, lastKnownChange);
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
