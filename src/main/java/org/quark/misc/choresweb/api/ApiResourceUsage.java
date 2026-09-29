package org.quark.misc.choresweb.api;

public record ApiResourceUsage(long resourceId, int points, String notes) {
	public long resourceId() {
		return resourceId;
	}

	public int points() {
		return points;
	}

	public String notes() {
		return notes;
	}
}