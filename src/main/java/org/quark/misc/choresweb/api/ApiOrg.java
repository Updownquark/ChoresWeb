package org.quark.misc.choresweb.api;

import java.time.Instant;

import org.quark.misc.choresweb.entities.Organization;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

public record ApiOrg(long id, @NotNull String name, Instant lastActive) {
	public long id() {
		return id;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String name() {
		return name;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Instant lastActive() {
		return lastActive;
	}

	public static ApiOrg of(Organization dbOrg) {
		return new ApiOrg(dbOrg.getId(), dbOrg.getName(), dbOrg.getLastActive());
	}
}
