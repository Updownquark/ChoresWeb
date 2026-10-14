package org.quark.misc.choresweb.entities;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user",
indexes = { //
	@Index(name = "users_by_email", columnList = "email", unique = true),//
})
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Getter
	private long id;

	@Column(length = 100, nullable = false)
	@Getter
	@Setter
	private String email;

	@Column(length = 100, nullable = false)
	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private boolean god;

	@Getter
	@Setter
	private boolean globalAdmin;

	@Getter
	@Setter
	private boolean blocked;

	@Column(name = "last_active", columnDefinition = "TIMESTAMP", nullable = true)
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Getter
	@Setter
	private Instant lastActive;

	/** Hibernate constructor */
	protected User() {}

	public User(String email) {
		this.email = email;
		int at = email.indexOf('@');
		name = at <= 0 ? email : email.substring(0, at);
		// Don't initialize last active because this time is only for logins by the user
	}

	@Override
	public String toString() {
		return email;
	}
}
