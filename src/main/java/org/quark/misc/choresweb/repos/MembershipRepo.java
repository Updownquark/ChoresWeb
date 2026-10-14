package org.quark.misc.choresweb.repos;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

public interface MembershipRepo extends JpaRepository<Membership, Long> {
	@Query("FROM Membership WHERE member=:user")
	List<Membership> getMembership(@Param("user") User user);

	@Query("FROM Membership WHERE organization=:org")
	List<Membership> getMembership(@Param("org") Organization org);

	@Query("FROM Membership WHERE member.id=:userId AND organization.id=:orgId")
	Membership getMembership(@Param("userId") long userId, @Param("orgId") long orgId);

	Membership getByMemberAndOrganizationId(User member, long orgId);

	List<Membership> getByOrganizationId(long orgId);

	List<Membership> getByMemberId(long userId);

	@Modifying
	void deleteByOrganization(Organization organization);

	@Query("FROM Membership m JOIN FETCH m.organization o"//
		+ " WHERE o.lastActive<:threshold"//
		+ " ORDER BY o.id")
	@QueryHints(@QueryHint(name = "jakarta.persistence.fetchSize", value = "100"))
	Stream<Membership> getStaleMemberships(@Param("threshold") Instant threshold);
}
