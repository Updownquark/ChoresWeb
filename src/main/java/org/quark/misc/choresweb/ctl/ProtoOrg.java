package org.quark.misc.choresweb.ctl;

import java.time.Instant;

import org.quark.misc.choresweb.entities.Organization;

import jakarta.validation.constraints.NotNull;

public record ProtoOrg(long id, @NotNull String name, Instant lastActive) {
	public static ProtoOrg of(Organization dbOrg) {
		return new ProtoOrg(dbOrg.getId(), dbOrg.getName(), dbOrg.getLastActive());
	}
}
