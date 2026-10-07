package org.quark.misc.choresweb.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.quark.misc.choresweb.entities.Membership;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiMembership(long id, ApiOrg organization, ApiUser member, @JsonInclude(JsonInclude.Include.NON_NULL) String name,
	@JsonInclude(JsonInclude.Include.NON_NULL) Instant lastActive, boolean manager, boolean worker, int level, long points,
	@JsonInclude(JsonInclude.Include.NON_NULL) Set<String> labels) {

	public static ApiMembership of(Membership membership) {
		return new ApiMembership(membership.getId(), //
			ApiOrg.of(membership.getOrganization()), //
			ApiUser.of(membership.getMember()), //
			membership.getName(), membership.getLastActive(), membership.isManager(), membership.isWorker(), membership.getLevel(),
			membership.getPoints(), splitLabels(membership.getLabels()));
	}

	public static Set<String> splitLabels(String labels) {
		if (labels == null || StringUtils.isAllBlank(labels))
			return Collections.emptySet();
		String[] split = labels.split(",");
		Set<String> labelSet = new LinkedHashSet<>();
		for (String label : split)
			labelSet.add(label.trim());
		return labelSet;
	}
}
