package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.Assignment;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiAssignment(long userId, long jobId, int completed, String notes, boolean deleted) {
	public long userId() {
		return userId;
	}

	public long jobId() {
		return jobId;
	}

	public int completed() {
		return completed;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String notes() {
		return notes;
	}

	public boolean deleted() {
		return deleted;
	}

	public static ApiAssignment of(Assignment entity) {
		return new ApiAssignment(//
			entity.getId().getWorker().getId(), //
			entity.getId().getJob().getId(), //
			entity.getCompleted(), //
			entity.getNotes(), false);
	}

	public static ApiAssignment deleted(Assignment entity) {
		return deleted(entity.getId().getWorker().getId(), entity.getId().getJob().getId());
	}

	public static ApiAssignment deleted(long userId, long jobId) {
		return new ApiAssignment(userId, jobId, 0, null, true);
	}
}
