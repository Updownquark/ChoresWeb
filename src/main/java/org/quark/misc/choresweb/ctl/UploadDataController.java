package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.UploadDataService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadDataController {
	private final OrganizationService theMembershipSvc;
	private final UploadDataService theUploadService;

	public UploadDataController(OrganizationService membershipSvc, UploadDataService uploadService) {
		theMembershipSvc = membershipSvc;
		theUploadService = uploadService;
	}

	@PostMapping(value = "/upload", consumes = "multipart/form-data")
	public void uploadBackup(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId,
		@RequestParam(required = true) MultipartFile file) {
		Membership membership = theMembershipSvc.getOrganization(user.getClaimAsString("email"), orgId);
		if (!membership.isManager())
			throw new UnsupportedOperationException("You do not have permission to upload a backup for this organization");
		theUploadService.uploadBackup(membership.getId().getOrganization(), () -> file.getResource().getInputStream());
	}
}
