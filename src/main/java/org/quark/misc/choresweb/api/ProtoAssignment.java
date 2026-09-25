package org.quark.misc.choresweb.api;

import org.quark.misc.choresweb.entities.Assignment;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ProtoAssignment(long userId, long jobId, int completed, String notes, boolean deleted) {
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

	public static ProtoAssignment of(Assignment entity) {
		return new ProtoAssignment(//
			entity.getId().getWorker().getId(), //
			entity.getId().getJob().getId(), //
			entity.getCompleted(), //
			entity.getNotes(), false);
	}

	public static ProtoAssignment deleted(long userId, long jobId) {
		return new ProtoAssignment(userId, jobId, 0, null, true);
	}
}
