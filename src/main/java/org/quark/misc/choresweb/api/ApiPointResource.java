package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.PointResource;

public record ApiPointResource(long id, long organization, String name, double rate, String unit, boolean deleted) {
	public long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public double rate() {
		return rate;
	}

	public String unit() {
		return unit;
	}

	public boolean deleted() {
		return deleted;
	}

	public static ApiPointResource of(PointResource resource) {
		return new ApiPointResource(resource.getId(), resource.getOrganization().getId(), resource.getName(), resource.getRate(),
			resource.getUnit(), false);
	}

	public static ApiPointResource deleted(PointResource resource) {
		return new ApiPointResource(resource.getId(), resource.getOrganization().getId(), null, 0.0, null, true);
	}
}
