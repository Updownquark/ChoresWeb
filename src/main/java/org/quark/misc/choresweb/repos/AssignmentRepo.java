package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.Assignment;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepo extends JpaRepository<Assignment, Long> {
	@Query("FROM Assignment WHERE organization=:org")
	List<Assignment> getAssignments(@Param("org") Organization org);

	@Modifying
	default void deleteForMember(@Param("member") Membership member) {
		deleteByOrganizationAndWorker(member.getOrganization(), member.getMember());
	}

	@Query("DELETE FROM Assignment assn WHERE assn.organization=:org AND assn.worker=:worker")
	@Modifying
	void deleteByOrganizationAndWorker(Organization org, User worker);

	@Query("DELETE FROM Assignment assn1 WHERE assn1.job=:job AND assn1.worker=:worker")
	@Modifying
	void deleteByJobAndWorker(@Param("job") Job job, @Param("worker") User worker);

	@Modifying
	void deleteByOrganization(Organization organization);

	@Query("FROM Assignment assn2 WHERE assn2.job=:job AND assn2.worker=:worker")
	Assignment getByJobAndWorker(@Param("job") Job job, @Param("worker") User worker);
}
