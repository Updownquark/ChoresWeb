package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.User;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ProtoUser(long id, String email, boolean canCreateOrgs) {
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

	public static ProtoUser of(User dbUser, boolean canCreateOrgs) {
		return new ProtoUser(dbUser.getId(), dbUser.getEmail(), canCreateOrgs);
	}
}