package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepo extends JpaRepository<Job, Long> {
	@Query("FROM Job WHERE organization=:org")
	public List<Job> getOrgJobs(@Param("org") Organization org);

	@Query("SELECT COUNT(*) FROM Job org WHERE org.name=:name")
	int getByName(@Param("name") String name);

	@Modifying
	void deleteByOrganization(Organization organization);

	public boolean existsByOrganizationIdAndName(long orgId, String name);
}
