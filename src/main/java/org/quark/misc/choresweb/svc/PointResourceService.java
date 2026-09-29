package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ApiPointResource;
import org.quark.misc.choresweb.api.ApiResourceUsage;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

@Service
public class PointResourceService {
	private final PointResourceRepo theResourceRepo;
	private final UserService theUserService;
	private final PointHistoryService theHistoryService;

	private final EntityChangeSet<Long, OrgGroupedResource> theChanges = new EntityChangeSet<>(rsrc -> rsrc.resource.id(), 15000);

	public PointResourceService(PointResourceRepo resourceRepo, UserService userService, PointHistoryService historyService) {
		theResourceRepo = resourceRepo;
		theUserService = userService;
		theHistoryService = historyService;
	}

	@Transactional(readOnly = true)
	public List<PointResource> getResources(Organization org) {
		return theResourceRepo.getOrgResources(org);
	}

	@Transactional(readOnly = true)
	public EntityChangeSet.ChangeSet<ApiPointResource> getApiResources(Membership me) {
		return theChanges.getValues(() -> theResourceRepo.getOrgResources(me.getId().getOrganization()).stream()//
			.map(ApiPointResource::of)//
			.toList());
	}

	@Transactional(readOnly = true)
	public PointResource getById(Membership member, long resourceId) {
		PointResource resource;
		try {
			resource = theResourceRepo.getReferenceById(resourceId);
		} catch (EntityNotFoundException e) {
			return null;
		}
		if (resource == null || resource.getOrganization().getId() != member.getId().getOrganization().getId())
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
		PointResource resource = new PointResource(member.getId().getOrganization(), name);
		if (configure != null)
			configure.accept(resource);
		theResourceRepo.save(resource);
		theChanges.changed(new OrgGroupedResource(resource));
		return resource;
	}

	@Transactional
	public void modifyResource(Membership member, long resourceId, Predicate<PointResource> modify) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to modify resources in this organization");
		PointResource resource;
		try {
			resource = theResourceRepo.getReferenceById(resourceId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException();
		}
		if (resource == null || resource.getOrganization().getId() != member.getId().getOrganization().getId())
			throw new NoSuchElementException();
		if (modify.test(resource)) {
			theResourceRepo.save(resource);
			theChanges.changed(new OrgGroupedResource(resource));
		}
	}

	@Transactional
	public void deleteResource(Membership member, long resourceId) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to remove resources from this organization");
		PointResource resource;
		try {
			resource = theResourceRepo.getReferenceById(resourceId);
		} catch (EntityNotFoundException e) {
			throw new NoSuchElementException();
		}
		if (resource == null || resource.getOrganization().getId() != member.getId().getOrganization().getId())
			throw new NoSuchElementException();
		theResourceRepo.delete(resource);
		theChanges.changed(OrgGroupedResource.delete(resource));
	}

	@Transactional
	public void redeemPoints(Membership me, long workerId, List<ApiResourceUsage> usage) {
		if (!me.isManager())
			throw new UnsupportedOperationException("You do not have permission to enact resource usage for this organization");
		Membership worker = theUserService.getMembership(me.getId().getOrganization().getId(), workerId);
		if (worker == null)
			throw new IllegalArgumentException("The given user is not a member of this organization");
		else if (!worker.isWorker())
			throw new IllegalArgumentException("The given user is not a worker in this organization");
		List<PointChangeRecord> history = new ArrayList<>(usage.size());
		for (var rsrc : usage) {
			PointResource resource = theResourceRepo.findById(rsrc.resourceId()).orElse(null);
			if (resource == null || resource.getOrganization() != me.getId().getOrganization())
				throw new IllegalArgumentException("No such resource with ID " + rsrc.resourceId() + " in this organization");
			double amount = rsrc.points() * resource.getRate();
			PointChangeRecord record = new PointChangeRecord(resource, worker, Instant.now(), amount);
			record.setNotes(rsrc.notes());
			history.add(record);
			worker.setPoints(record.getBeforePoints() + record.getPointChange());
		}
		theUserService.memberUpdated(worker);
		theHistoryService.historyAdded(history);
	}

	public void resourceUpdated(PointResource rsrc) {
		theChanges.changed(new OrgGroupedResource(rsrc));
	}

	public EntityChangeSet.ChangeSet<ApiPointResource> getChanges(long orgId, long lastKnownChange) {
		return theChanges.getChanges(lastKnownChange, rsrc -> rsrc.orgId == orgId, rsrc -> rsrc.resource);
	}

	static class OrgGroupedResource {
		final long orgId;
		final ApiPointResource resource;

		OrgGroupedResource(long orgId, ApiPointResource resource) {
			this.orgId = orgId;
			this.resource = resource;
		}

		OrgGroupedResource(PointResource resource) {
			orgId = resource.getOrganization().getId();
			this.resource = ApiPointResource.of(resource);
		}

		static OrgGroupedResource delete(PointResource resource) {
			return new OrgGroupedResource(resource.getOrganization().getId(), ApiPointResource.deleted(resource));
		}
	}
}
