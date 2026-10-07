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
	@JsonInclude(JsonInclude.Include.NON_NULL) Set<String> labels, boolean deleted) {

	public static ApiMembership of(Membership membership, boolean withOrg, boolean withUser) {
		return new ApiMembership(membership.getId(), //
			withOrg ? ApiOrg.of(membership.getOrganization()) : null, //
				withUser ? ApiUser.of(membership.getMember()) : null, //
					membership.getName(), membership.getLastActive(), membership.isManager(), membership.isWorker(), membership.getLevel(),
					membership.getPoints(), splitLabels(membership.getLabels()), false);
	}

	public static ApiMembership deleted(Membership membership, boolean withOrg, boolean withUser) {
		return new ApiMembership(membership.getId(), //
			withOrg ? ApiOrg.of(membership.getOrganization()) : null, //
				withUser ? ApiUser.of(membership.getMember()) : null, //
					null, null, false, false, 0, 0, null, true);
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
