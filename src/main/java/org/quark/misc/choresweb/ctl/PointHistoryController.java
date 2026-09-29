package org.quark.misc.choresweb.ctl;

import java.util.List;

import org.quark.misc.choresweb.api.ApiMembership;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.PointChangeRecord;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.PointHistoryService;
import org.quark.misc.choresweb.svc.PointHistoryService.HistoryChanges;
import org.quark.misc.choresweb.svc.UserService;
import org.quark.misc.choresweb.util.EntityChangeSet;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class PointHistoryController {
	private final OrganizationService theMembershipSvc;
	private final PointHistoryService theHistoryService;
	private final UserService theUserSvc;

	public PointHistoryController(OrganizationService membershipSvc, PointHistoryService historyService, UserService userSvc) {
		theMembershipSvc = membershipSvc;
		theHistoryService = historyService;
		theUserSvc = userSvc;
	}

	@GetMapping
	public List<PointChangeRecord.FullPcrDto> getHistory(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = true) long orgId, //
		@RequestParam(required = false) Long userId, //
		@RequestParam(required = false) Long jobId, //
		@RequestParam(required = false) Long resourceId, //
		@RequestParam(required = true) int pageSize, //
		@RequestParam(required = true) int pageNumber) {
		Membership me = theMembershipSvc.getMe(user, orgId);
		return theHistoryService.getHistory(me, userId, jobId, resourceId, pageSize, pageNumber);
	}

	@GetMapping("/size")
	public int getHistorySize(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = true) long orgId, //
		@RequestParam(required = false) Long userId, //
		@RequestParam(required = false) Long jobId, //
		@RequestParam(required = false) Long resourceId) {
		Membership me = theMembershipSvc.getMe(user, orgId);
		return theHistoryService.getHistorySize(me, userId, jobId, resourceId);
	}

	@GetMapping("/changes")
	public HistoryChanges getChanges(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = true) long orgId, //
		@RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getMe(user, orgId);
		return theHistoryService.getChanges(orgId, lastKnownChange);
	}

	@DeleteMapping
	public EntityChangeSet.ChangeSet<ApiMembership> revertHistory(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = true) long orgId, //
		@RequestParam(required = true) List<Long> items, //
		@RequestParam(required = true) long lastKnownChange) {
		Membership me = theMembershipSvc.getMe(user, orgId);
		theHistoryService.revertHistory(me, items);
		return theUserSvc.getChanges(orgId, lastKnownChange);
	}
}
