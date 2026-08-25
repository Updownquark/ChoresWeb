package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.Assignment;
import org.quark.misc.choresweb.entities.AssignmentId;
import org.quark.misc.choresweb.entities.Job;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepo extends JpaRepository<Assignment, AssignmentId> {
	@Query("FROM Assignment WHERE organization=:org")
	List<Assignment> getAssignments(@Param("org") Organization org);

	default void deleteForMember(@Param("member") Membership member) {
		deleteByOrganizationAndWorker(member.getId().getOrganization(), member.getId().getMember());
	}

	@Query("DELETE FROM Assignment assn WHERE assn.organization=:org AND assn.id.worker=:worker")
	void deleteByOrganizationAndWorker(Organization org, User worker);

	@Query("DELETE FROM Assignment assn1 WHERE assn1.id.job=:job AND assn1.id.worker=:worker")
	void deleteByJobAndWorker(@Param("job") Job job, @Param("worker") User worker);

	void deleteByOrganization(Organization organization);

	@Query("FROM Assignment assn2 WHERE assn2.id.job=:job AND assn2.id.worker=:worker")
	Assignment getByJobAndWorker(@Param("job") Job job, @Param("worker") User worker);
}
