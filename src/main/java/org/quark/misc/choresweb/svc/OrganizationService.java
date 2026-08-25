package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.OrgsRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrganizationService {
	private final OrgsRepo theOrgRepo;
	private final UserService theUserSvc;
	private final MembershipRepo theMembershipRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final AssignmentRepo theAssnRepo;
	private final JobRepo theJobRepo;
	private final PointResourceRepo theResourceRepo;

	public OrganizationService(OrgsRepo orgRepo, UserService userSvc, MembershipRepo membershipRepo, PointChangeRecordRepo pointChangeRepo,
		AssignmentRepo assnRepo, JobRepo jobRepo, PointResourceRepo resourceRepo) {
		theOrgRepo = orgRepo;
		theUserSvc = userSvc;
		theMembershipRepo = membershipRepo;
		thePointChangeRepo = pointChangeRepo;
		theAssnRepo = assnRepo;
		theJobRepo = jobRepo;
		theResourceRepo = resourceRepo;
	}

	@Transactional(readOnly = true)
	public List<Membership> getAvailableOrgs(User user) {
		return theMembershipRepo.getMembership(user);
	}

	@Transactional(readOnly = true)
	public Membership getOrganization(String userEmail, long id) {
		User user = theUserSvc.getUser(userEmail);
		if (user == null)
			return null;
		theUserSvc.userActive(user);
		Membership membership = theMembershipRepo.getMembership(user.getId(), id);
		if (membership == null)
			throw new NoSuchElementException("Organization does not exist or you are not a member");
		return membership;
	}

	@Transactional
	public Membership addOrganization(String userEmail, String name) {
		if (!theUserSvc.canCreateOrgs(userEmail))
			throw new UnsupportedOperationException("You do not have permission to create organizations");
		String newName = StringUtils.getNewItemName(n -> theOrgRepo.getByName(name) > 0, name, StringUtils.SIMPLE_DUPLICATES);
		Organization org = new Organization(newName);
		theOrgRepo.save(org);
		User user = theUserSvc.getOrCreateUser(userEmail);
		theUserSvc.userActive(user);
		Membership membership = new Membership(org, user);
		membership.setManager(true);
		membership.setLastActive(Instant.now());
		theMembershipRepo.save(membership);
		return membership;
	}

	@Transactional
	public void setOrganizationName(Membership member, String name) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify this organization");
		if (name.equals(member.getId().getOrganization().getName()))
			return;
		if (name.length() < 3 || name.length() > Organization.NAME_LENGTH)
			throw new IllegalArgumentException("Organization name must be between 3 and " + Organization.NAME_LENGTH + " characters");
		else if (!name.trim().equals(name))
			throw new IllegalArgumentException("Organization name cannot start or end with white space");
		else if (theOrgRepo.getByName(name) > 0)
			throw new IllegalArgumentException("A different organization named '" + name + "' exists");
		member.getId().getOrganization().setName(name);
		theOrgRepo.save(member.getId().getOrganization());
	}

	@Transactional
	public void deleteOrganization(Membership member) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to delete this organization");
		theJobRepo.deleteByOrganization(member.getId().getOrganization());
		theResourceRepo.deleteByOrganization(member.getId().getOrganization());
		thePointChangeRepo.deleteByOrganization(member.getId().getOrganization());
		theAssnRepo.deleteByOrganization(member.getId().getOrganization());
		theOrgRepo.delete(member.getId().getOrganization());
	}
}
