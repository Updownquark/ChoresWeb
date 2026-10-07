package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.Assignment;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiAssignment(long id, long userId, long jobId, long organization, int completed,
	@JsonInclude(JsonInclude.Include.NON_NULL) String notes, boolean deleted) {
	public static ApiAssignment of(Assignment entity) {
		return new ApiAssignment(//
			entity.getId(),
			entity.getWorker().getId(), //
			entity.getJob().getId(), //
			entity.getJob().getOrganization().getId(), //
			entity.getCompleted(), //
			entity.getNotes(), false);
	}

	public static ApiAssignment deleted(Assignment entity) {
		return deleted(entity.getId(), entity.getWorker().getId(), entity.getJob().getId(), entity.getJob().getOrganization().getId());
	}

	public static ApiAssignment deleted(long id, long userId, long jobId, long organization) {
		return new ApiAssignment(id, userId, jobId, organization, 0, null, true);
	}
}
