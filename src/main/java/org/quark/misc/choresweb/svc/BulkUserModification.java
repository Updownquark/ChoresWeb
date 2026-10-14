package org.quark.misc.choresweb.svc;

import org.quark.misc.choresweb.api.ApiUser;
import org.quark.misc.choresweb.entities.User;

public class BulkUserModification {
	public final Boolean god;
	public final Boolean globalAdmin;
	public final Boolean blocked;

	public BulkUserModification(Boolean god, Boolean globalAdmin, Boolean blocked) {
		this.god = god;
		this.globalAdmin = globalAdmin;
		this.blocked = blocked;
	}

	public ApiUser modify(User user) {
		boolean userGod = god != null ? god : user.isGod();
		boolean userGA = globalAdmin != null ? globalAdmin : user.isGlobalAdmin();
		boolean userBlocked = blocked != null ? blocked : user.isBlocked();
		return new ApiUser(user.getId(), user.getEmail(), userGod, userGA, userBlocked);
	}
}
