package org.quark.misc.choresweb.repos;

import org.quark.misc.choresweb.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgsRepo extends JpaRepository<Organization, Long> {
	@Query("SELECT COUNT(*) FROM Organization org WHERE org.name=:name")
	int getByName(@Param("name") String name);
}
