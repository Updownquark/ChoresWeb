package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.svc.ImportExportService;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/backup")
@CrossOrigin(origins = "http://localhost:5173", exposedHeaders = "Content-Disposition")
public class ImportExportController {
	private final OrganizationService theMembershipSvc;
	private final ImportExportService theBackupService;

	public ImportExportController(OrganizationService membershipSvc, ImportExportService uploadService) {
		theMembershipSvc = membershipSvc;
		theBackupService = uploadService;
	}

	@GetMapping("/export")
	public ResponseEntity<StreamingResponseBody> exportData(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		if (!membership.isManager())
			throw new UnsupportedOperationException("You do not have permission to export backup data for this organization");

		return ResponseEntity.ok()//
			.contentType(MediaType.APPLICATION_OCTET_STREAM)//
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + membership.getOrganization().getName() + ".zip" + "\"")//
			.body(out -> theBackupService.exportData(membership.getOrganization(), out));
	}

	@PostMapping(value = "/import", consumes = "multipart/form-data")
	public void importData(@AuthenticationPrincipal Jwt user, @RequestParam(required = true) long orgId,
		@RequestParam(required = true) MultipartFile file) {
		Membership membership = theMembershipSvc.getMe(user, orgId);
		if (!membership.isManager())
			throw new UnsupportedOperationException("You do not have permission to import backup data for this organization");
		theBackupService.importData(membership, () -> file.getResource().getInputStream());
	}
}
