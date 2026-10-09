package org.quark.misc.choresweb.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "application_info")
public class ApplicationInfo {
	@Id
	@Column(name = "singleton_id", nullable = false, insertable = false, updatable = false)
	private int singletonId = 1;

	@Getter
	@Setter
	private boolean openToWorld;
}
