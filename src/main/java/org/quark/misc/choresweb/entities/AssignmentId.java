package org.quark.misc.choresweb.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

@Embeddable
public class AssignmentId {
	@ManyToOne(optional = false)
	@JoinColumn(name = "job")
	@Getter
	private Job job;

	@ManyToOne(optional = false)
	@JoinColumn(name = "worker")
	@Getter
	private User worker;

	protected AssignmentId() {
	}

	public AssignmentId(Job job, User worker) {
		this.job = job;
		this.worker = worker;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(job.getId()) ^ Integer.rotateLeft(Long.hashCode(worker.getId()), 16);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		else if (!(o instanceof AssignmentId))
			return false;
		AssignmentId other = (AssignmentId) o;
		return job.equals(other.job) && worker.equals(other.worker);
	}
}
