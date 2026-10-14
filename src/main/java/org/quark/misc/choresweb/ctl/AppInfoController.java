package org.quark.misc.choresweb.ctl;

import org.quark.misc.choresweb.entities.ApplicationInfo;
import org.quark.misc.choresweb.svc.ApplicationInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppInfoController {
	private final ApplicationInfoService theAppInfoSvc;

	public AppInfoController(ApplicationInfoService appInfoSvc) {
		theAppInfoSvc = appInfoSvc;
	}

	@GetMapping("/info")
	public ApplicationInfo getAppInfo() {
		return theAppInfoSvc.getApplicationInfo();
	}
}
