package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.api.ProtoMembership;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.ModifyWorkerCommand;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.UserService;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonAlias;

@RestController
@RequestMapping("/api/members")
public class UsersController {
	private final UserService theUserSvc;
	private final OrganizationService theMembershipSvc;

	public UsersController(UserService userSvc, OrganizationService membershipSvc) {
		theUserSvc = userSvc;
		theMembershipSvc = membershipSvc;
	}

	@GetMapping("/by-org/{orgId}")
	public EntityChangeSet.ChangeSet<ProtoMembership> getMembers(@AuthenticationPrincipal Jwt user,
		@PathVariable(required = true) long orgId) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId);
		return theUserSvc.getApiMembers(me);
	}

	@GetMapping("/changes/{orgId}")
	public EntityChangeSet.ChangeSet<ProtoMembership> getMemberChanges(@AuthenticationPrincipal Jwt user,
		@PathVariable(required = true) long orgId, @RequestParam(required = true) long lastKnownChange) {
		theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId); // Ensure the user has access
		return theUserSvc.getChanges(orgId, lastKnownChange);
	}

	@PostMapping("/add")
	public EntityChangeSet.ChangeSet<ProtoMembership> addMember(@AuthenticationPrincipal Jwt user, @RequestBody AddMember command,
		@RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), command.organization());
		theUserSvc.addWorker(me, command.userEmail());
		return theUserSvc.getChanges(me.getId().getOrganization().getId(), lastKnownChange);
	}

	@PostMapping("/modify")
	public EntityChangeSet.ChangeSet<ProtoMembership> modifyMember(@AuthenticationPrincipal Jwt user,
		@RequestBody ModifyWorkerCommand command, @RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), command.orgId());
		theUserSvc.modifyWorker(me, command);
		return theUserSvc.getChanges(me.getId().getOrganization().getId(), lastKnownChange);
	}

	@DeleteMapping
	public EntityChangeSet.ChangeSet<ProtoMembership> removeMember(@AuthenticationPrincipal Jwt user, @RequestBody RemoveMember command,
		@RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), command.organization());
		theUserSvc.removeWorker(me, command.user());
		return theUserSvc.getChanges(me.getId().getOrganization().getId(), lastKnownChange);
	}

	public record AddMember(long organization, @JsonAlias("user-email") String userEmail) {}

	public record RemoveMember(long organization, long user) {}
}
