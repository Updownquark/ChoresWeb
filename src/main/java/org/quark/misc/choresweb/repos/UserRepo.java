package org.quark.misc.choresweb.repos;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

public interface UserRepo extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, CustomUserRepo {
	User getByEmail(String email);

	@Query("SELECT u FROM User u"//
		+ " WHERE NOT u.god"//
		+ " AND u.lastActive<:threshold"//
		+ " AND NOT EXISTS (SELECT m FROM Membership m WHERE m.member = u)")
	@QueryHints(@QueryHint(name = "jakarta.persistence.fetchSize", value = "100"))
	Stream<User> getPurgeableUsers(@Param("threshold") Instant now);

	@Query("SELECT COUNT(*) FROM User")
	int getUserCount();

	@Modifying
	@Query("UPDATE User u SET u.god = :god WHERE u.id IN :userIds")
	void setGodWhereIdIn(@Param("userIds") Collection<Long> userIds, @Param("god") boolean god);

	@Modifying
	@Query("UPDATE User u SET u.globalAdmin = :globalAdmin WHERE u.id IN :userIds")
	void setGlobalAdminWhereIdIn(@Param("userIds") Collection<Long> userIds, @Param("globalAdmin") boolean globalAdmin);

	@Modifying
	@Query("UPDATE User u SET u.blocked = :blocked WHERE u.id IN :userIds")
	void setBlockedWhereIdIn(@Param("userIds") Collection<Long> userIds, @Param("blocked") boolean blocked);

	static Specification<User> emailEqual(String emailPattern) {
		return (root, query, cb) -> cb.equal(root.get("email"), emailPattern);
	}

	static Specification<User> emailLike(String emailPattern) {
		return (root, query, cb) -> cb.like(root.get("email"), emailPattern);
	}

	static Specification<User> nameEqual(String emailPattern) {
		return (root, query, cb) -> cb.equal(root.get("name"), emailPattern);
	}

	static Specification<User> nameLike(String namePattern) {
		return (root, query, cb) -> cb.like(root.get("name"), namePattern);
	}

	static Specification<User> lastActive(boolean greater, boolean equal, Instant threshold) {
		if (greater) {
			if (equal)
				return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastActive"), threshold);
				else
					return (root, query, cb) -> cb.greaterThan(root.get("lastActive"), threshold);
		} else {
			if (equal)
				return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("lastActive"), threshold);
				else
					return (root, query, cb) -> cb.lessThan(root.get("lastActive"), threshold);
		}
	}

	static Specification<User> isGod(boolean god) {
		return (root, query, cb) -> cb.equal(root.get("god"), god);
	}

	static Specification<User> isGlobalAdmin(boolean globalAdmin) {
		return (root, query, cb) -> cb.equal(root.get("globalAdmin"), globalAdmin);
	}

	static Specification<User> isBlocked(boolean blocked) {
		return (root, query, cb) -> cb.equal(root.get("blocked"), blocked);
	}

	static Specification<User> idIn(Collection<Long> ids) {
		return (root, query, cb) -> {
			return root.get("id").in(ids);
		};
	}

	static Specification<User> emailIn(Collection<String> emails) {
		return (root, query, cb) -> {
			if (emails == null || emails.isEmpty()) {
				return cb.disjunction(); // Returns empty if no emails provided
			}

			// Ensure the input list is strictly lowercase to match the DB evaluation
			List<String> lowerEmails = emails.stream()//
				.map(String::toLowerCase)//
				.collect(Collectors.toList());

			// Generates: WHERE LOWER(user.email) IN ('email1', 'email2', ...)
			return cb.lower(root.get("email")).in(lowerEmails);
		};
	}
}
