package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.api.ApiUser;
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
	public ApiUser getMe(@AuthenticationPrincipal Jwt user) {
		String email = user.getClaimAsString("email");
		if (email == null)
			email = user.getSubject();
		User dbUser = theUserSvc.getUserCreateIfGod(email);
		if (dbUser != null)
			return ApiUser.of(dbUser);
		else
			return new ApiUser(-1, email, false, false);
	}
}
