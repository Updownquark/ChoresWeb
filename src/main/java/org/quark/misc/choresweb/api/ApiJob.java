package org.quark.misc.choresweb.api;

import java.time.Instant;
import java.util.Set;

import org.quark.misc.choresweb.entities.Job;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiJob(long id, long organization, @JsonInclude(JsonInclude.Include.NON_NULL) String name, int value, int minLevel,
	int maxLevel,
	@JsonInclude(JsonInclude.Include.NON_NULL) Set<String> inclusionLabels, //
	@JsonInclude(JsonInclude.Include.NON_NULL) Set<String> exclusionLabels, //
	int priority, boolean active, Instant lastDone) {

	public ApiJob(long id) {
		this(id, -1, null, 0, 0, 0, null, null, 0, false, null);
	}

	public static ApiJob of(Job job) {
		return new ApiJob(job.getId(), job.getOrganization().getId(), job.getName(), job.getValue(), job.getMinLevel(), job.getMaxLevel(), //
			ApiMembership.splitLabels(job.getInclusionLabels()), ApiMembership.splitLabels(job.getExclusionLabels()), //
			job.getPriority(), job.isActive(), job.getLastDone());
	}
}
