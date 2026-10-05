package org.quark.misc.choresweb.entities;

import java.time.Instant;

import org.qommons.Named;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job", indexes = { //
		@Index(name = "jobs_by_org, name", columnList = "organization", unique = true)//
})
public class Job implements Named {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "organization")
	@Getter
	private Organization organization;

	@Column(length = 60, nullable = false)
	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private int value;

	@Getter
	@Setter
	private int minLevel;
	@Getter
	@Setter
	private int maxLevel;
	@Column(length = 120)
	@Getter
	@Setter
	private String inclusionLabels;
	@Column(length = 120)
	@Getter
	@Setter
	private String exclusionLabels;
	@Getter
	@Setter
	private int priority;

	@Getter
	@Setter
	private boolean active;

	@Column(name = "last_done", columnDefinition = "TIMESTAMP", nullable = true)
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Getter
	@Setter
	private Instant lastDone;

	/** Hibernate constructor */
	protected Job() {
	}

	public Job(Organization org, String name) {
		this.organization = org;
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
