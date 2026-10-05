package org.quark.misc.choresweb.ctl;

import java.util.Collections;
import java.util.List;

import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.User;
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

import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/orgs")
public class OrgsController {
	private final UserService theUserSvc;
	private final OrganizationService theOrgSvc;

	OrgsController(UserService userSvc, OrganizationService orgSvc) {
		theUserSvc = userSvc;
		theOrgSvc = orgSvc;
	}

	@GetMapping
	public List<ApiMembership> getAvailable(@AuthenticationPrincipal Jwt user) {
		User dbUser = theUserSvc.getMe(user);
		if (dbUser == null)
			return Collections.emptyList();
		return theOrgSvc.getAvailableOrgs(dbUser).stream()//
			.map(org -> ApiMembership.of(org, true, false))//
			.toList();
	}

	@GetMapping("/{id}")
	public ApiMembership get(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
		Membership org = theOrgSvc.getMe(user, id);
		return ApiMembership.of(org, true, false);
	}

	@PostMapping("/add")
	public ApiMembership add(@AuthenticationPrincipal Jwt user) {
		Membership org = theOrgSvc.addOrganization(UserService.getUserEmail(user));
		return ApiMembership.of(org, true, false);
	}

	@PostMapping("set-name")
	public ApiMembership setName(@AuthenticationPrincipal Jwt user, @RequestBody ModifyOrg org) {
		Membership found = theOrgSvc.getMe(user, org.id());
		theOrgSvc.setOrganizationName(found, org.name());
		return ApiMembership.of(found, true, false);
	}

	@DeleteMapping("/{id}")
	public void delete(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
		theOrgSvc.deleteOrganization(theOrgSvc.getMe(user, id));
	}

	public record ModifyOrg(long id, @NotNull String name) {}
}
