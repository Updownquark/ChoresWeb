package org.quark.misc.choresweb.entities;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "organization",
	indexes = { //
		@Index(name = "orgs_by_name", columnList = "name", unique = true)//
	})
public class Organization {
	public static final int NAME_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private long id;

	@Column(length = NAME_LENGTH, nullable = false)
	@Getter
	@Setter
	private String name;

	@Column(name = "last_active", columnDefinition = "TIMESTAMP", nullable = true)
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Getter
	@Setter
	private Instant lastActive;

	/** Hibernate constructor */
	@SuppressWarnings("unused")
	private Organization() {
	}

	public Organization(String name) {
		this.name = name;
	}
}
