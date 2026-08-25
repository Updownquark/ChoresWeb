package org.quark.misc.choresweb.ctl;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.quark.misc.choresweb.entities.Membership;

public record ProtoMembership(ProtoOrg org, ProtoUser member, Instant lastActive, boolean manager, boolean worker, int level, long points,
	Set<String> labels) {

	public static ProtoMembership of(Membership membership, boolean withOrg, boolean withUser) {
		return new ProtoMembership(//
			withOrg ? ProtoOrg.of(membership.getId().getOrganization()) : null, //
			withUser ? ProtoUser.of(membership.getId().getMember(), false) : null, //
			membership.getLastActive(), membership.isManager(), membership.isWorker(), membership.getLevel(), membership.getPoints(),
			splitLabels(membership.getLabels()));
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
