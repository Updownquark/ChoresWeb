package org.quark.misc.choresweb.ctl;

import java.util.Collections;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
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
	public List<ProtoMembership> getAvailable(@AuthenticationPrincipal Jwt user) {
		User dbUser = theUserSvc.getUser(user.getClaimAsString("email"));
		if (dbUser == null)
			return Collections.emptyList();
		return theOrgSvc.getAvailableOrgs(dbUser).stream()//
			.map(org -> ProtoMembership.of(org, true, false))//
			.toList();
	}

	@GetMapping("/{id}")
	public ProtoMembership get(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
		Membership org = theOrgSvc.getOrganization(user.getClaimAsString("email"), id);
		return ProtoMembership.of(org, true, false);
	}

	@PostMapping("/add")
	public ProtoMembership add(@AuthenticationPrincipal Jwt user, @RequestParam("name") String name) {
		Membership org = theOrgSvc.addOrganization(user.getClaimAsString("email"), name);
		return ProtoMembership.of(org, true, false);
	}

	@PostMapping("set-name")
	public ProtoMembership setName(@AuthenticationPrincipal Jwt user, @RequestBody ModifyOrg org) {
		Membership found = theOrgSvc.getOrganization(user.getClaimAsString("email"), org.id());
		theOrgSvc.setOrganizationName(found, org.name());
		return ProtoMembership.of(found, true, false);
	}

	@DeleteMapping("/{id}")
	public void delete(@AuthenticationPrincipal Jwt user, @PathVariable long id) {
		theOrgSvc.deleteOrganization(theOrgSvc.getOrganization(user.getClaimAsString("email"), id));
	}

	public record ModifyOrg(long id, @NotNull String name) {}
}
