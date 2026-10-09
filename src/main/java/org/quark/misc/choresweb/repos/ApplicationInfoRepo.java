package org.quark.misc.choresweb.repos;

import org.quark.misc.choresweb.entities.ApplicationInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationInfoRepo extends JpaRepository<ApplicationInfo, Integer> {
	default ApplicationInfo getSingleton() {
		return findById(1).orElse(null);
	}
}
