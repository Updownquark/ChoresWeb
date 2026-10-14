package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.User;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiUser(long id, @JsonInclude(JsonInclude.Include.NON_NULL) String email, boolean god, boolean globalAdmin, boolean blocked) {
	public static ApiUser of(User dbUser) {
		return new ApiUser(dbUser.getId(), dbUser.getEmail(), dbUser.isGod(), dbUser.isGlobalAdmin(), dbUser.isBlocked());
	}
}