package org.quark.misc.choresweb.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.quark.misc.choresweb.entities.Membership;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ProtoMembership(ProtoOrg organization, ProtoUser member, String name, Instant lastActive, boolean manager, boolean worker,
	int level, long points, Set<String> labels, boolean deleted) {

	public ProtoOrg organization() {
		return organization;
	}

	public ProtoUser member() {
		return member;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String name() {
		return name;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Instant lastActive() {
		return lastActive;
	}

	public boolean manager() {
		return manager;
	}

	public boolean worker() {
		return worker;
	}

	public int level() {
		return level;
	}

	public long points() {
		return points;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Set<String> labels() {
		return labels;
	}

	public boolean deleted() {
		return deleted;
	}

	public static ProtoMembership of(Membership membership, boolean withOrg, boolean withUser) {
		return new ProtoMembership(//
			withOrg ? ProtoOrg.of(membership.getId().getOrganization()) : null, //
			withUser ? ProtoUser.of(membership.getId().getMember(), false) : null, //
			membership.getName(), membership.getLastActive(), membership.isManager(), membership.isWorker(), membership.getLevel(),
			membership.getPoints(), splitLabels(membership.getLabels()), false);
	}

	public static ProtoMembership deleted(Membership membership, boolean withOrg, boolean withUser) {
		return new ProtoMembership(//
			withOrg ? ProtoOrg.of(membership.getId().getOrganization()) : null, //
			withUser ? ProtoUser.of(membership.getId().getMember(), false) : null, //
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
