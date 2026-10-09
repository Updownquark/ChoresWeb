package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiOrg;
import org.quark.misc.choresweb.api.ApiPointResource;
import org.quark.misc.choresweb.api.ApiResourceUsage;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.sync.SyncDataSource;
import org.quark.misc.choresweb.sync.SyncService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PointResourceService {
	private final PointResourceRepo theResourceRepo;
	private final UserService theUserService;
	private final OrganizationService theOrgService;
	private final PointHistoryService theHistoryService;
	private final EntityMutationNotificationService theNotificationSvc;

	public PointResourceService(PointResourceRepo resourceRepo, UserService userService, OrganizationService orgService,
		PointHistoryService historyService, EntityMutationNotificationService notificationSvc, SyncService<User> syncService,
		ObjectMapper objectMapper) {
		theResourceRepo = resourceRepo;
		theUserService = userService;
		theOrgService = orgService;
		theHistoryService = historyService;
		theNotificationSvc = notificationSvc;

		theNotificationSvc.installSerializer("resource", ApiPointResource.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApiPointResource.class, objectMapper));
		syncService.installDataSource(new SyncDataSource.SingleFieldValidationDataSource<User, ApiPointResource, Long>("resource",
			ApiPointResource.class, objectMapper, "organization") {
			@Override
			protected String isAuthorized(User user, Long authFieldValue) {
				Membership me = theOrgService.getMembership(user, authFieldValue);
				if (me == null)
					return "No such organization with ID " + authFieldValue + " or you are not a member of it";
				return null;
			}

			@Override
			protected ValidationMaintainer<User> maintainValidation(User user, Long authFieldValue) {
				long userId = user.getId(); // Release the user object to garbage collection
				long orgId = authFieldValue.longValue();
				return event -> {
					if (!event.isPresent() && event.getEntity() instanceof ApiMembership membership) {
						if (membership.member().id() == userId && membership.organization().id() == orgId) {
							// The user's membership in the organization has been revoked
							return ValidationChange.RevokeSubscription;
						}
					} else if (!event.isPresent() && event.getEntity() instanceof ApiOrg && ((ApiOrg) event.getEntity()).id() == orgId) {
						return ValidationChange.RevokeSubscription;
					}
					return ValidationChange.Ignore;// Still valid
				};
			}

			@Override
			protected Stream<ApiPointResource> getEntities(Long authFieldValue) {
				return theResourceRepo.getByOrganizationId(authFieldValue).stream().map(ApiPointResource::of);
			}
		});
	}

	@Transactional(readOnly = true)
	public List<PointResource> getResources(Organization org) {
		return theResourceRepo.getOrgResources(org);
	}

	@Transactional(readOnly = true)
	public List<ApiPointResource> getApiResources(Membership me) {
		return theResourceRepo.getOrgResources(me.getOrganization()).stream()//
			.map(ApiPointResource::of)//
			.toList();
	}

	@Transactional(readOnly = true)
	public PointResource getById(Jwt user, long resourceId) {
		PointResource resource;
		try {
			resource = theResourceRepo.getReferenceById(resourceId);
		} catch (EntityNotFoundException e) {
			return null;
		}
		if (resource == null)
			return null;
		return resource;
	}

	@Transactional(readOnly = true)
	public boolean hasResourceNamed(long orgId, String name) {
		return theResourceRepo.existsByOrganizationIdAndName(orgId, name);
	}

	@Transactional
	public PointResource createResource(Membership member, Consumer<PointResource> configure) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to add resources to this organization");
		String name = StringUtils.getNewItemName(n -> theResourceRepo.getByName(n) > 0, "A Resource", StringUtils.SIMPLE_DUPLICATES);
		PointResource resource = new PointResource(member.getOrganization(), name);
		if (configure != null)
			configure.accept(resource);
		theResourceRepo.save(resource);
		theNotificationSvc.publishMutation("resource", true, ApiPointResource.of(resource));
		theUserService.updateOrg(member.getOrganization());
		return resource;
	}

	@Transactional
	public PointResource modifyResource(Membership member, long resourceId, Predicate<PointResource> modify) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify resources in this organization");
		PointResource resource;
		try {
			resource = theResourceRepo.getReferenceById(resourceId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException();
		}
		if (resource == null || resource.getOrganization().getId() != member.getOrganization().getId())
			throw new NoSuchElementException();
		if (modify.test(resource)) {
			theResourceRepo.save(resource);
			theNotificationSvc.publishMutation("resource", true, ApiPointResource.of(resource));
			theUserService.updateOrg(member.getOrganization());
		}
		return resource;
	}

	@Transactional
	public void deleteResource(Jwt user, long resourceId) {
		PointResource rsrc = theResourceRepo.findById(resourceId).orElse(null);
		if (rsrc == null)
			throw new NoSuchElementException("No such resource visible");
		Membership me = theOrgService.getMe(user, rsrc.getOrganization().getId());
		if (rsrc.getOrganization().getId() != me.getOrganization().getId())
			throw new NoSuchElementException("No such resource visible");
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to remove resources from this organization");
		theResourceRepo.delete(rsrc);
		theNotificationSvc.publishMutation("resource", false, ApiPointResource.of(rsrc));
		theUserService.updateOrg(me.getOrganization());
	}

	@Transactional
	public void redeemPoints(Membership me, long workerId, List<ApiResourceUsage> usage) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to enact resource usage for this organization");
		Membership worker = theUserService.getMembership(me.getOrganization().getId(), workerId);
		if (worker == null)
			throw new IllegalArgumentException("The given user is not a member of this organization");
		else if (!worker.isWorker())
			throw new IllegalArgumentException("The given user is not a worker in this organization");
		List<PointChangeRecord> history = new ArrayList<>(usage.size());
		for (var rsrc : usage) {
			PointResource resource = theResourceRepo.findById(rsrc.resourceId()).orElse(null);
			if (resource == null || resource.getOrganization() != me.getOrganization())
				throw new IllegalArgumentException("No such resource with ID " + rsrc.resourceId() + " in this organization");
			double amount = rsrc.points() * resource.getRate();
			PointChangeRecord record = new PointChangeRecord(resource, worker, Instant.now(), amount);
			record.setNotes(rsrc.notes());
			history.add(record);
			worker.setPoints(record.getBeforePoints() + record.getPointChange());
		}
		theUserService.memberUpdated(worker);
		theHistoryService.historyAdded(history);
		theUserService.updateOrg(me.getOrganization());
	}

	public void resourceUpdated(PointResource rsrc) {
		theNotificationSvc.publishMutation("resource", true, ApiPointResource.of(rsrc));
	}
}
