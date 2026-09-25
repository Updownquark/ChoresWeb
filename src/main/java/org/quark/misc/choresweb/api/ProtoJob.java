package org.quark.misc.choresweb.api;

import java.util.Set;

import org.quark.misc.choresweb.entities.Job;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ProtoJob(long id, String name, int value, int minLevel, int maxLevel, Set<String> inclusionLabels,
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

	public static ProtoJob of(Job job) {
		return new ProtoJob(job.getId(), job.getName(), job.getValue(), job.getMinLevel(), job.getMaxLevel(), //
			ProtoMembership.splitLabels(job.getInclusionLabels()), ProtoMembership.splitLabels(job.getExclusionLabels()), //
			job.getPriority(), job.isActive(), false);
	}

	public static ProtoJob deleted(long jobId) {
		return new ProtoJob(jobId, null, 0, 0, 0, null, null, 0, false, true);
	}
}
