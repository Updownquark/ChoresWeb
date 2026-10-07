package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.svc.UserService;
import org.quark.misc.choresweb.sync.SyncService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController extends org.quark.misc.choresweb.sync.SyncController<User> {
	private final UserService theUserService;

	public SyncController(UserService userService, SyncService<User> syncService) {
		super(syncService);
		theUserService = userService;
	}

	@Override
	protected User getMe(Jwt user) {
		return theUserService.getUserCreateIfGod(UserService.getUserEmail(user));
	}

	@Override
	protected Object getUserId(User user) {
		return user.getId();
	}
}
