package org.quark.misc.choresweb.repos;

import java.util.List;

import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.domain.Specification;

public interface CustomUserRepo {
	List<Long> findAllIds(Specification<User> spec);
}
