package org.quark.misc.choresweb.ctl;

import java.util.List;

import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiUser;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.svc.ModifyUserCommand;
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

	@GetMapping("/by-org/{orgId}")
	public List<ApiMembership> getMembers(@AuthenticationPrincipal Jwt user,
		@PathVariable(required = true) long orgId) {
		Membership me = theMembershipSvc.getMe(user, orgId);
		return theUserSvc.getApiMembers(me);
	}

	@GetMapping("{id}")
	public ApiMembership getMember(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
		Membership member = theMembershipSvc.getMembershipById(user, id);
		return member == null ? null : ApiMembership.of(member);
	}

	@PostMapping("/add")
	public ApiMembership addMember(@AuthenticationPrincipal Jwt user, @RequestBody AddMember command) {
		Membership me = theMembershipSvc.getMe(user, command.orgId());
		Membership member = theUserSvc.addWorker(me, command.userEmail(), null);
		return ApiMembership.of(member);
	}

	@PostMapping("/modify")
	public ApiMembership modifyMember(@AuthenticationPrincipal Jwt user, @RequestBody ModifyWorkerCommand command) {
		Membership me = theMembershipSvc.getMe(user, command.orgId());
		Membership member = theUserSvc.modifyWorker(me, command);
		return ApiMembership.of(member);
	}

	@DeleteMapping
	public void removeMember(@AuthenticationPrincipal Jwt user, @RequestBody RemoveMember command) {
		Membership me = theMembershipSvc.getMe(user, command.orgId());
		theUserSvc.removeWorker(me, command.userId());
	}

	@PostMapping
	public ApiUser modifyUser(@AuthenticationPrincipal Jwt user, @RequestBody ModifyUserCommand command) {
		User modUser = theUserSvc.modifyUser(user, command);
		return ApiUser.of(modUser);
	}

	public record AddMember(long orgId, @JsonAlias("user-email") String userEmail) {}

	public record RemoveMember(long orgId, long userId) {}
}
