package org.quark.misc.choresweb.ctl;

import java.util.List;

import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.ModifyWorkerCommand;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

	@GetMapping("/{orgId}")
	public List<ProtoMembership> getMembers(@AuthenticationPrincipal Jwt user, @PathVariable long orgId) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId);
		return theUserSvc.getMembers(me).stream()//
			.map(org -> ProtoMembership.of(org, false, true))//
			.toList();
	}

	@PostMapping("/add")
	public ProtoMembership addMember(@AuthenticationPrincipal Jwt user, @RequestBody AddMember command) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), command.organization());
		return ProtoMembership.of(theUserSvc.addWorker(me, command.userEmail()), false, true);
	}

	@PostMapping("/modify")
	public ProtoMembership modifyMember(@AuthenticationPrincipal Jwt user, @RequestBody ModifyWorkerCommand command) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), command.organization());
		return ProtoMembership.of(theUserSvc.modifyWorker(me, command), false, true);
	}

	@DeleteMapping
	public void removeMember(@AuthenticationPrincipal Jwt user, @RequestBody RemoveMember command) {
		Membership me = theMembershipSvc.getOrganization(user.getClaimAsString("email"), command.organization());
		theUserSvc.removeWorker(me, command.user());
	}

	public record AddMember(long organization, @JsonAlias("user-email") String userEmail) {}

	public record RemoveMember(long organization, long user) {}

}
