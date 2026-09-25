package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.api.ProtoUser;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.svc.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeService {
	private final UserService theUserSvc;

	public MeService(UserService userSvc) {
		theUserSvc = userSvc;
	}

	@GetMapping("/me")
	public ProtoUser getMe(@AuthenticationPrincipal Jwt user) {
		String email = user.getClaimAsString("email");
		User dbUser = theUserSvc.getUserCreateIfAdmin(email);
		if (dbUser != null)
			return ProtoUser.of(dbUser, theUserSvc.canCreateOrgs(email));
		else
			return new ProtoUser(-1, email, false);
	}
}
