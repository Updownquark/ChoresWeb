package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.quark.misc.choresweb.api.ChoresApplicationEvent;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.AssignmentRepo;
import org.quark.misc.choresweb.repos.JobRepo;
import org.quark.misc.choresweb.repos.MembershipRepo;
import org.quark.misc.choresweb.repos.OrgsRepo;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
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
	private final ApplicationEventPublisher theEventPublisher;

	public OrganizationService(OrgsRepo orgRepo, UserService userSvc, MembershipRepo membershipRepo, PointChangeRecordRepo pointChangeRepo,
		AssignmentRepo assnRepo, JobRepo jobRepo, PointResourceRepo resourceRepo, ApplicationEventPublisher eventPublisher) {
		theOrgRepo = orgRepo;
		theUserSvc = userSvc;
		theMembershipRepo = membershipRepo;
		thePointChangeRepo = pointChangeRepo;
		theAssnRepo = assnRepo;
		theJobRepo = jobRepo;
		theResourceRepo = resourceRepo;
		theEventPublisher = eventPublisher;
		System.out.println("Initializing OrgService");
	}

	@Transactional(readOnly = true)
	public List<Membership> getAvailableOrgs(User user) {
		return theMembershipRepo.getMembership(user);
	}

	@Transactional(readOnly = true)
	public Membership getMembershipById(Jwt user, long id) {
		Membership member = theMembershipRepo.findById(id).orElse(null);
		if (member == null)
			return null;
		getMe(user, member.getOrganization().getId());
		return member;
	}

	@Transactional
	public Membership getMe(Jwt authUser, long orgId) {
		User user = theUserSvc.getMe(authUser);
		if (user == null)
			throw new NoSuchElementException("Organization does not exist or you are not a member");
		theUserSvc.userActive(user);
		Membership membership = theMembershipRepo.getMembership(user.getId(), orgId);
		if (membership == null)
			throw new NoSuchElementException("Organization does not exist or you are not a member");
		return membership;
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
	public Membership addOrganization(String userEmail) {
		if (!theUserSvc.canCreateOrgs(userEmail))
			throw new UnsupportedOperationException("You do not have permission to create organizations");
		String newName = ChoresWebUtils.getNewName(theOrgRepo.findAll(), 200, "Org");
		Organization org = new Organization(newName);
		theOrgRepo.save(org);
		User user = theUserSvc.getOrCreateUser(userEmail);
		theUserSvc.userActive(user);
		Membership membership = new Membership(org, user);
		membership.setName(userEmail);
		membership.setManager(true);
		membership.setLastActive(Instant.now());
		theMembershipRepo.save(membership);
		// Fire the security change first so the add event can get to them
		theEventPublisher.publishEvent(ChoresApplicationEvent.securityMutation(this, org.getId(), user.getId(), true));
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, org.getId(), "organization", org.getId(), true));
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, org.getId(), "membership", user.getId(), true));
		return membership;
	}

	@Transactional
	public void setOrganizationName(Membership member, String name) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify this organization");
		if (name.equals(member.getOrganization().getName()))
			return;
		if (name.length() < 3 || name.length() > Organization.NAME_LENGTH)
			throw new IllegalArgumentException("Organization name must be between 3 and " + Organization.NAME_LENGTH + " characters");
		else if (!name.trim().equals(name))
			throw new IllegalArgumentException("Organization name cannot start or end with white space");
		else if (theOrgRepo.getByName(name) > 0)
			throw new IllegalArgumentException("A different organization named '" + name + "' exists");
		member.getOrganization().setName(name);
		theOrgRepo.save(member.getOrganization());
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, member.getOrganization().getId(), "organization",
			member.getOrganization().getId(), true));
	}

	@Transactional
	public void deleteOrganization(Membership member) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to delete this organization");
		thePointChangeRepo.deleteByOrganization(member.getOrganization());
		theAssnRepo.deleteByOrganization(member.getOrganization());
		theJobRepo.deleteByOrganization(member.getOrganization());
		theResourceRepo.deleteByOrganization(member.getOrganization());
		theMembershipRepo.deleteByOrganization(member.getOrganization());
		theOrgRepo.delete(member.getOrganization());
		theEventPublisher.publishEvent(ChoresApplicationEvent.dataChange(this, member.getOrganization().getId(), "organization",
			member.getOrganization().getId(), false));
	}
}
