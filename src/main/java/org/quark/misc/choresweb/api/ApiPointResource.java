package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.PointResource;

public record ApiPointResource(long id, long organization, String name, double rate, String unit) {
	public static ApiPointResource of(PointResource resource) {
		return new ApiPointResource(resource.getId(), resource.getOrganization().getId(), resource.getName(), resource.getRate(),
			resource.getUnit());
	}
}
