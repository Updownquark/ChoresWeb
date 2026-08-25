package org.quark.misc.choresweb.ctl;

import java.util.Set;

import org.quark.misc.choresweb.entities.Job;

public record ProtoJob(long id, String name, int value, int minLevel, int maxLevel, Set<String> inclusionLabels,
	Set<String> exclusionLabels, int priority, boolean active) {

	public static ProtoJob of(Job job) {
		return new ProtoJob(job.getId(), job.getName(), job.getValue(), job.getMinLevel(), job.getMaxLevel(), //
			ProtoMembership.splitLabels(job.getInclusionLabels()), ProtoMembership.splitLabels(job.getExclusionLabels()), //
			job.getPriority(), job.isActive());
	}
}
