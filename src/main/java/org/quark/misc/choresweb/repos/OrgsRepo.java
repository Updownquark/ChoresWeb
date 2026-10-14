package org.quark.misc.choresweb.repos;

import java.util.stream.Stream;

import org.quark.misc.choresweb.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

public interface OrgsRepo extends JpaRepository<Organization, Long> {
	@Query("SELECT COUNT(*) FROM Organization org WHERE org.name=:name")
	int getByName(@Param("name") String name);

	@Query("SELECT o FROM Organization o"//
		+ " WHERE NOT EXISTS (SELECT m FROM Membership m WHERE m.organization = o)")
	@QueryHints(@QueryHint(name = "jakarta.persistence.fetchSize", value = "100"))
	Stream<Organization> getOrphanedOrgs();

	@Query("SELECT COUNT(*) FROM Organization")
	int getOrganizationCount();
}
