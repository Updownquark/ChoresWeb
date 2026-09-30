package org.quark.misc.choresweb.ctl;

import java.util.Collections;
import java.util.List;

import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.api.ApiPointResource;
import org.quark.misc.choresweb.api.ApiResourceUsage;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.PointResource;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.PointResourceService;
import org.quark.misc.choresweb.svc.UserService;
import org.quark.misc.choresweb.util.ChoresWebUtils;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class PointResourceController {
	private final OrganizationService theMembershipSvc;
	private final PointResourceService theResourceService;
	private final UserService theUserSvc;

	public PointResourceController(OrganizationService membershipSvc, PointResourceService jobService, UserService userSvc) {
		theMembershipSvc = membershipSvc;
		theResourceService = jobService;
		theUserSvc = userSvc;
	}

	@GetMapping("/by-org/{orgId}")
	public EntityChangeSet.ChangeSet<ApiPointResource> getJobs(@AuthenticationPrincipal Jwt user, @PathVariable("orgId") long orgId) {
		System.out.println("Getting " + orgId + " jobs");
		return theResourceService.getApiResources(theMembershipSvc.getMe(user, orgId));
	}

	@GetMapping("/{id}")
	public ApiPointResource getJob(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId,
		@PathVariable("orgId") long id) {
		return ApiPointResource
			.of(theResourceService.getById(theMembershipSvc.getMe(user, orgId), id));
	}

	@GetMapping("/changes/{orgId}")
	public EntityChangeSet.ChangeSet<ApiPointResource> getResourceChanges(@AuthenticationPrincipal Jwt user,
		@PathVariable("orgId") long orgId, @RequestParam(required = true) long lastKnownChange) {
		theMembershipSvc.getMe(user, orgId); // Ensure the user has access
		return theResourceService.getChanges(orgId, lastKnownChange);
	}

	@PutMapping
	public EntityChangeSet.ChangeSet<ApiPointResource> addOrModifyResource(@AuthenticationPrincipal Jwt user, //
		@RequestBody ResourceAddOrMod action, //
		@RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getMe(user, action.orgId());
		boolean withRate = action.rate() != null;
		if (withRate) {
			if (Math.abs(action.rate) < 0.01)
				throw new IllegalArgumentException("Rate must not be zero");
			else if (action.rate > 1000)
				throw new IllegalArgumentException("Rate cannot exceed 1000");
		}
		boolean withUnit = action.unit() != null;
		if (withUnit) {
			if (action.unit().length() == 0) { // Fine
			} else if (action.unit().length() > 16)
				throw new IllegalArgumentException("Unit cannot exceed 16 characters");
		}
		if (action.resourceId != null) { // Modify a job
			theResourceService.modifyResource(membership, action.resourceId, rsrc -> {
				boolean mod = withRate || withUnit;
				boolean withName = action.name != null && !action.name.equals(rsrc.getName());
				if (withName) {
					if (action.name.length() == 0)
						throw new IllegalArgumentException("Name cannot be empty");
					else if (action.name.length() > 60)
						throw new IllegalArgumentException("Name cannot exceed 60 characters");
					else if (theResourceService.hasResourceNamed(action.orgId(), action.name))
						throw new IllegalArgumentException("Another resource named '" + action.name + "' exists");
					mod = true;
				}
				if (withName)
					rsrc.setName(action.name());
				if (withRate)
					rsrc.setRate(action.rate());
				if (withUnit)
					rsrc.setUnit(action.unit().length() == 0 ? null : action.unit());
				return mod;
			});
		} else { // Add a resource
			String newName = ChoresWebUtils.getNewName(theResourceService.getResources(membership.getId().getOrganization()), 60, //
				action.name(), "A Resource"); // So the new resource is at the top, easy to find

			theResourceService.createResource(membership, newRsrc -> {
				newRsrc.setName(newName);
				if (withRate)
					newRsrc.setRate(action.rate());
				if (withUnit && action.unit().length() > 0)
					newRsrc.setUnit(action.unit());
			});
		}
		return theResourceService.getChanges(action.orgId(), lastKnownChange);
	}

	@DeleteMapping("/{id}")
	public EntityChangeSet.ChangeSet<ApiPointResource> deleteResource(@AuthenticationPrincipal Jwt user, @PathVariable long id,
		@RequestParam(required = true) long lastKnownChange) {
		PointResource rsrc = theResourceService.getById(null, id);
		if (rsrc == null)
			return new EntityChangeSet.ChangeSet<>(lastKnownChange, Collections.emptyList());
		Membership membership = theMembershipSvc.getMe(user, rsrc.getOrganization().getId());
		if (!membership.isManager())
			throw new UnsupportedOperationException("You do not have permission to delete jobs in this organization");
		theResourceService.deleteResource(membership, id);
		return theResourceService.getChanges(rsrc.getOrganization().getId(), lastKnownChange);
	}

	@PostMapping("/redeem")
	public EntityChangeSet.ChangeSet<ApiMembership> redeemPoints(@AuthenticationPrincipal Jwt user,
		@RequestBody(required = true) ResourceUsageCall usage, @RequestParam(required = true) long lastKnownChange) {
		Membership membership = theMembershipSvc.getMe(user, usage.orgId);
		if (!membership.isManager())
			throw new UnsupportedOperationException("You do not have permission to enact resource usage for this organization");
		theResourceService.redeemPoints(membership, usage.userId, usage.usage);
		return theUserSvc.getChanges(usage.orgId, lastKnownChange);
	}

	public static record ResourceAddOrMod(long orgId, Long resourceId, String name, Double rate, String unit) {
		public long orgId() {
			return orgId;
		}

		public Long resourceId() {
			return resourceId;
		}

		public String name() {
			return name;
		}

		public Double rate() {
			return rate;
		}

		public String unit() {
			return unit;
		}
	}

	public static record ResourceUsageCall(long orgId, long userId, List<ApiResourceUsage> usage) {
		public long orgId() {
			return orgId;
		}

		public long userId() {
			return userId;
		}

		public List<ApiResourceUsage> usage() {
			return usage;
		}
	}
}
