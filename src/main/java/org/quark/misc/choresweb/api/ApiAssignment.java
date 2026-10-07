package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.Assignment;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiAssignment(long id, long userId, long jobId, long organization, int completed,
	@JsonInclude(JsonInclude.Include.NON_NULL) String notes) {
	public static ApiAssignment of(Assignment entity) {
		return new ApiAssignment(//
			entity.getId(),
			entity.getWorker().getId(), //
			entity.getJob().getId(), //
			entity.getJob().getOrganization().getId(), //
			entity.getCompleted(), //
			entity.getNotes());
	}
}
