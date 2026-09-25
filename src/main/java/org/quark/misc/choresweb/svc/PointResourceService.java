package org.quark.misc.choresweb.svc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.qommons.StringUtils;
import org.quark.misc.choresweb.api.ProtoPointResource;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.Organization;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.repos.PointChangeRecordRepo;
import org.quark.misc.choresweb.repos.PointResourceRepo;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

@Service
public class PointResourceService {
	private final PointResourceRepo theResourceRepo;
	private final PointChangeRecordRepo thePointChangeRepo;
	private final UserService theUserService;

	private final EntityChangeSet<Long, OrgGroupedResource> theChanges = new EntityChangeSet<>(rsrc -> rsrc.resource.id(), 15000);

	public PointResourceService(PointResourceRepo resourceRepo, PointChangeRecordRepo pointChangeRepo, UserService userService) {
		theResourceRepo = resourceRepo;
		thePointChangeRepo = pointChangeRepo;
		theUserService = userService;
	}

	@Transactional(readOnly = true)
	public List<PointResource> getResources(Organization org) {
		return theResourceRepo.getOrgResources(org);
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

	@Transactional
	public PointResource createResource(Membership member) {
		if (!member.isManager())
			throw new UnsupportedOperationException("You do not have permission to add resources to this organization");
		String name = StringUtils.getNewItemName(n -> theResourceRepo.getByName(n) > 0, "A Resource", StringUtils.SIMPLE_DUPLICATES);
		PointResource resource = new PointResource(member.getId().getOrganization(), name);
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
	public PointChangeRecord usePoints(PointResource resource, Membership worker, float amount) {
		PointChangeRecord record = new PointChangeRecord(resource, worker, Instant.now(), amount);
		thePointChangeRepo.save(record);
		worker.setPoints(record.getBeforePoints() + record.getPointChange());
		theUserService.memberUpdated(worker);
		return record;
	}

	public void resourceUpdated(PointResource rsrc) {
		theChanges.changed(new OrgGroupedResource(rsrc));
	}

	static class OrgGroupedResource {
		final long orgId;
		final ProtoPointResource resource;

		OrgGroupedResource(long orgId, ProtoPointResource resource) {
			this.orgId = orgId;
			this.resource = resource;
		}

		OrgGroupedResource(PointResource resource) {
			orgId = resource.getOrganization().getId();
			this.resource = ProtoPointResource.of(resource);
		}

		static OrgGroupedResource delete(PointResource resource) {
			return new OrgGroupedResource(resource.getOrganization().getId(), ProtoPointResource.deleted(resource));
		}
	}
}
