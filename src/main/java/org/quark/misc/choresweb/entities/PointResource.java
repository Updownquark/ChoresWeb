package org.quark.misc.choresweb.entities;

import org.qommons.Named;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "point_resource", indexes = { //
		@Index(name = "resources_by_org", columnList = "organization, name", unique = true)//
})
public class PointResource implements Named {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "organization")
	@Getter
	@Setter(value = AccessLevel.PRIVATE)
	private Organization organization;

	@Column(length = 60, nullable = false)
	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private double rate = 1;

	@Column(length = 16)
	@Getter
	@Setter
	private String unit;

	protected PointResource() {
	}

	public PointResource(Organization org, String name) {
		this.organization = org;
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
