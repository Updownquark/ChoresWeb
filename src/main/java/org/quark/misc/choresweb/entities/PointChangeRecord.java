package org.quark.misc.choresweb.entities;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "point_change_record")
public class PointChangeRecord {
	@Id
	@Getter
	private long id;

	public enum PointChangeType {
		Job, Resource
	}

	@ManyToOne
	@JoinColumn(name = "organization")
	@Getter
	private Organization organization;

	@ManyToOne
	@JoinColumn(name = "worker")
	@Getter
	private User worker;

	@Column(length = 16, nullable = false)
	@Enumerated(EnumType.STRING)
	@Getter
	private PointChangeType changeType;

	@Getter
	private long changeSourceId;

	@Column(nullable = false, columnDefinition = "TIMESTAMP")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Instant time;

	@Column(length = 60, nullable = false)
	@Getter
	private String changeSourceName;

	@Getter
	@Setter
	private long beforePoints;

	@Getter
	@Setter
	private int pointChange;

	@Getter
	@Setter
	private double quantity;

	@Getter
	@Setter
	private double valueOrRate;

	@Column(length = 100)
	@Getter
	@Setter
	private String notes;

	private PointChangeRecord() {}

	public PointChangeRecord(Job job, Membership worker, Instant time, int points) {
		organization = job.getOrganization();
		this.worker = worker.getId().getMember();
		changeType = PointChangeType.Job;
		changeSourceId = job.getId();
		this.time = time;
		changeSourceName = job.getName();
		beforePoints = worker.getPoints();
		pointChange = points;
		quantity = points;
		valueOrRate = job.getValue();
	}

	public PointChangeRecord(PointResource resource, Membership worker, Instant time, double quantity) {
		organization = resource.getOrganization();
		this.worker = worker.getId().getMember();
		changeType = PointChangeType.Resource;
		changeSourceId = resource.getId();
		this.time = time;
		changeSourceName = resource.getName();
		beforePoints = worker.getPoints();
		this.quantity = quantity;
		valueOrRate = resource.getRate();
		pointChange = (int) Math.round(quantity * valueOrRate);
	}
}
