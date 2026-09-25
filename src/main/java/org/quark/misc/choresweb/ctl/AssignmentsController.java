package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.api.ProtoAssignment;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.OrganizationService;
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

	public AssignmentsController(OrganizationService membershipSvc, WorkService workService) {
		theMembershipSvc = membershipSvc;
		theWorkService = workService;
	}

	@GetMapping("/by-org/{orgId}")
	public EntityChangeSet.ChangeSet<ProtoAssignment> getAssignments(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId) {
		return theWorkService.getApiAssignments(theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId));
	}

	@GetMapping("/changes/{orgId}")
	public EntityChangeSet.ChangeSet<ProtoAssignment> getAssignmentChanges(@AuthenticationPrincipal Jwt user,
		@PathVariable("orgId") long orgId, @RequestParam(required = true) long lastKnownChange) {
		theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId); // Ensure the user has access
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@PutMapping
	public EntityChangeSet.ChangeSet<ProtoAssignment> addOrModifyAssignment(@AuthenticationPrincipal Jwt user,
		@RequestBody AssnAddOrMod action, @RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), action.orgId());
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
	public EntityChangeSet.ChangeSet<ProtoAssignment> deleteAssignment(@AuthenticationPrincipal Jwt user,
		@RequestParam(required = true) long orgId, @RequestParam(required = true) long userId, @RequestParam(required = true) long jobId,
		@RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId);
		theWorkService.deleteAssignment(membership, jobId, userId);
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@DeleteMapping("/all")
	public EntityChangeSet.ChangeSet<ProtoAssignment> deleteAssignment(@AuthenticationPrincipal Jwt user,
		@RequestParam(required = true) long orgId, @RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId);
		theWorkService.clearAssignments(membership);
		return theWorkService.getChanges(orgId, lastKnownChange);
	}

	@PostMapping("/submit")
	public EntityChangeSet.ChangeSet<ProtoAssignment> submitAssignments(@AuthenticationPrincipal Jwt user,
		@RequestParam(required = true) long orgId, @RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId);
		theWorkService.commitAssignments(membership);
		return theWorkService.getChanges(orgId, lastKnownChange);
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
