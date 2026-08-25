package org.quark.misc.choresweb.entities;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "membership", indexes = { //
	@Index(name = "members_by_org", columnList = "organization, member", unique = true)//
})
public class Membership {
	@EmbeddedId
	@Getter
	private MembershipId id;

	@Getter
	@Setter
	private boolean manager;
	@Getter
	@Setter
	private boolean worker;

	@Getter
	@Setter
	private int level;
	@Column(length = 120)
	@Getter
	@Setter
	private String labels;

	@Getter
	@Setter
	private long points;

	@Column(name = "last_active", columnDefinition = "TIMESTAMP", nullable = true)
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Getter
	@Setter
	private Instant lastActive;

	/** Hibernate constructor */
	private Membership() {
	}

	public Membership(Organization org, User member) {
		id = new MembershipId(org, member);
	}
}
