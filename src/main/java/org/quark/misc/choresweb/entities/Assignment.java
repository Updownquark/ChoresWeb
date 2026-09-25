package org.quark.misc.choresweb.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "assignment", indexes = { //
		@Index(name = "assignments_by_org", columnList = "organization", unique = false) //
})
public class Assignment {
	@EmbeddedId
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private AssignmentId id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "organization")
	private Organization organization;

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
		id=new AssignmentId(job, worker);
		organization=job.getOrganization();
	}
}
