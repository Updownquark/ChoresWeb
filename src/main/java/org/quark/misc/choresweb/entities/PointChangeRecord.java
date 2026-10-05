package org.quark.misc.choresweb.entities;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "point_change_record",
	indexes = { //
		@Index(name = "point_changes_by_source", columnList = "organization,change_type,change_source_id,time"), //
		@Index(name = "work_by_worker", columnList = "organization,worker,time"),//
	})
public class PointChangeRecord implements Comparable<PointChangeRecord> {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	protected PointChangeRecord() {}

	public PointChangeRecord(Membership worker, Instant time, PointChangeType changeType, long changeSourceId, String changeSourceName,
		long beforePoints, int pointChange, double quantity, double valueOrRate) {
		this.organization = worker.getOrganization();
		this.worker = worker.getMember();
		this.time = time;
		this.changeType = changeType;
		this.changeSourceId = changeSourceId;
		this.changeSourceName = changeSourceName;
		this.beforePoints = beforePoints;
		this.pointChange = pointChange;
		this.quantity = quantity;
		this.valueOrRate = valueOrRate;
	}

	public PointChangeRecord(Job job, Membership worker, Instant time, int points) {
		organization = job.getOrganization();
		this.worker = worker.getMember();
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
		this.worker = worker.getMember();
		changeType = PointChangeType.Resource;
		changeSourceId = resource.getId();
		this.time = time;
		changeSourceName = resource.getName();
		beforePoints = worker.getPoints();
		this.quantity = quantity;
		valueOrRate = resource.getRate();
		pointChange = (int) Math.round(quantity * valueOrRate);
	}

	@Override
	public int compareTo(PointChangeRecord o) {
		int comp = time.compareTo(o.time);
		if (comp == 0)
			comp = Long.compare(id, o.id);
		return comp;
	}

	@Override
	public String toString() {
		return worker + " " + changeType + " " + changeSourceName + " (" + pointChange + ")";
	}

	public static record FullPcrDto(long id, long workerId, PointChangeType changeType, long changeSourceId, Instant time,
		String changeSourceName, long beforePoints, int pointChange, double quantity, double valueOrRate, String notes) {
	}

	public static record PcrKeyDto(long workerId, PointChangeType changeType, long changeSourceId, Instant time) {
	}
}
