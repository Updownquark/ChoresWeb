package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.entities.User;

public record ProtoUser(long id, String email, boolean canCreateOrgs) {
	public static ProtoUser of(User dbUser, boolean canCreateOrgs) {
		return new ProtoUser(dbUser.getId(), dbUser.getEmail(), canCreateOrgs);
	}
}