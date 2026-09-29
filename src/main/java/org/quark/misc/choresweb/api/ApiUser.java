package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.User;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiUser(long id, String email, boolean canCreateOrgs) {
	public long id() {
		return id;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String email() {
		return email;
	}

	public boolean canCreateOrgs() {
		return canCreateOrgs;
	}

	public static ApiUser of(User dbUser, boolean canCreateOrgs) {
		return new ApiUser(dbUser.getId(), dbUser.getEmail(), canCreateOrgs);
	}
}