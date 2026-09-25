package org.quark.misc.choresweb.api;

import java.time.Instant;

import org.quark.misc.choresweb.entities.Organization;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;

public record ProtoOrg(long id, @NotNull String name, Instant lastActive) {
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

	public static ProtoOrg of(Organization dbOrg) {
		return new ProtoOrg(dbOrg.getId(), dbOrg.getName(), dbOrg.getLastActive());
	}
}
