package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class CustomUserRepoImpl implements CustomUserRepo {
	private final EntityManager entityManager;

	public CustomUserRepoImpl(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<Long> findAllIds(Specification<User> spec) {
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<User> root = query.from(User.class);

		query.select(root.get("id"));

		if (spec != null) {
			Predicate predicate = spec.toPredicate(root, query, cb);
			if (predicate != null) {
				query.where(predicate);
			}
		}

		return entityManager.createQuery(query).getResultList();
	}
}
