package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.MembershipId;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepo extends JpaRepository<Membership, MembershipId> {
	@Query("FROM Membership WHERE id.member=:user")
	List<Membership> getMembership(@Param("user") User user);

	@Query("FROM Membership WHERE id.organization=:org")
	List<Membership> getMembership(@Param("org") Organization org);

	@Query("FROM Membership WHERE id.member.id=:userId AND id.organization.id=:orgId")
	Membership getMembership(@Param("userId") long userId, @Param("orgId") long orgId);
}
