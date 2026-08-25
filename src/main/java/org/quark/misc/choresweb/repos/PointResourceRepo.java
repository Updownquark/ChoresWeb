package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointResourceRepo extends JpaRepository<PointResource, Long> {
	@Query("FROM PointResource res WHERE res.organization=:org")
	public List<PointResource> getOrgResources(@Param("org") Organization org);

	@Query("SELECT COUNT(*) FROM PointResource org WHERE org.name=:name")
	int getByName(@Param("name") String name);

	void deleteByOrganization(Organization organization);
}
