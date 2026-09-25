package org.quark.misc.choresweb.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

@Embeddable
public class MembershipId {
	@ManyToOne(optional = false)
	@JoinColumn(name = "organization")
	@Getter
	private Organization organization;

	@ManyToOne(optional = false)
	@JoinColumn(name = "member")
	@Getter
	private User member;

	protected MembershipId() {}

	public MembershipId(Organization organization, User member) {
		this.organization = organization;
		this.member = member;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(organization.getId()) ^ Integer.rotateLeft(Long.hashCode(member.getId()), 16);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		else if (!(o instanceof MembershipId))
			return false;
		MembershipId other = (MembershipId) o;
		return organization.equals(other.organization) && member.equals(other.member);
	}

	@Override
	public String toString() {
		return member + ":" + organization;
	}
}
