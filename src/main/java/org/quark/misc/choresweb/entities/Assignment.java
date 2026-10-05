package org.quark.misc.choresweb.entities;

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
@Table(name = "assignment", indexes = { //
	@Index(name = "assignments_by_org", columnList = "organization", unique = false), //
	@Index(name = "assignments_by_job_and_worker", columnList = "job,worker", unique = true),
})
public class Assignment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "organization")
	private Organization organization;

	@ManyToOne(optional = false)
	@JoinColumn(name = "job")
	@Getter
	private Job job;

	@ManyToOne(optional = false)
	@JoinColumn(name = "worker")
	@Getter
	private User worker;

	@Getter
	@Setter
	private int completed;

	@Column(length = 100)
	@Getter
	@Setter
	private String notes;

	protected Assignment() {
	}

	public Assignment(Job job, User worker) {
		this.job = job;
		this.worker = worker;
		organization=job.getOrganization();
	}
}
