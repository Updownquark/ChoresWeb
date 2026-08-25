package org.quark.misc.choresweb.repos;

import org.quark.misc.choresweb.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<User, Long> {
	User getByEmail(String email);
}
