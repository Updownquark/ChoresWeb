package org.quark.misc.choresweb.api;

import java.util.Set;

import org.quark.misc.choresweb.entities.Job;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiJob(long id, String name, int value, int minLevel, int maxLevel, Set<String> inclusionLabels,
	Set<String> exclusionLabels, int priority, boolean active, boolean deleted) {

	public long id() {
		return id;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String name() {
		return name;
	}

	public int value() {
		return value;
	}

	public int minLevel() {
		return minLevel;
	}

	public int maxLevel() {
		return maxLevel;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Set<String> inclusionLabels() {
		return inclusionLabels;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Set<String> exclusionLabels() {
		return exclusionLabels;
	}

	public int priority() {
		return priority;
	}

	public boolean active() {
		return active;
	}

	public boolean deleted() {
		return deleted;
	}

	public static ApiJob of(Job job) {
		return new ApiJob(job.getId(), job.getName(), job.getValue(), job.getMinLevel(), job.getMaxLevel(), //
			ApiMembership.splitLabels(job.getInclusionLabels()), ApiMembership.splitLabels(job.getExclusionLabels()), //
			job.getPriority(), job.isActive(), false);
	}

	public static ApiJob deleted(long jobId) {
		return new ApiJob(jobId, null, 0, 0, 0, null, null, 0, false, true);
	}
}
