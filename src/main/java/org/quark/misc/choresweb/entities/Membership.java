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

	@Column(length = 100, nullable = false)
	@Getter
	@Setter
	private String name;

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
	protected Membership() {
	}

	public Membership(Organization org, User member) {
		id = new MembershipId(org, member);
	}

	@Override
	public String toString() {
		String str = name + "(" + id.toString();
		if (manager)
			str += ", manager";
		if (worker)
			str += ", worker";
		str += ")";
		if(labels!=null && labels.length()>0)
			str+="["+labels+"]";
		return str;
	}
}
