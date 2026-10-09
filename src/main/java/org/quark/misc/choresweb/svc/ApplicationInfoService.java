package org.quark.misc.choresweb.svc;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.quark.misc.choresweb.entities.ApplicationInfo;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.repos.ApplicationInfoRepo;
import org.quark.misc.choresweb.sync.EntityMutationNotificationService;
import org.quark.misc.choresweb.sync.SyncDataSource;
import org.quark.misc.choresweb.sync.SyncService;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class ApplicationInfoService {
	private final ApplicationInfoRepo theRepo;
	private final EntityMutationNotificationService theNotificationSvc;

	private ApplicationInfo theSingleton;

	public ApplicationInfoService(ApplicationInfoRepo repo, EntityMutationNotificationService notificationSvc,
		SyncService<User> syncService, ObjectMapper objectMapper) {
		theRepo = repo;
		theNotificationSvc = notificationSvc;

		theSingleton = theRepo.getSingleton();

		theNotificationSvc.installSerializer("applicationInfo", ApplicationInfo.class,
			new EntityMutationNotificationService.ReflectiveSerializer<>(ApplicationInfo.class, objectMapper));
		syncService.installDataSource(
			new SyncDataSource.AbstractReflectedDataSource<User, ApplicationInfo>("applicationInfo", ApplicationInfo.class, objectMapper) {
				@Override
				public ValidationMaintainer<User> validateSubscription(User user, Map<String, SyncDataFilter<ApplicationInfo>> filters)
					throws UnsupportedOperationException {
					// No constraints on application info query
					return _ -> SyncDataSource.ValidationChange.Ignore;
				}

				@Override
				public Collection<ApplicationInfo> queryEntities(List<Map<String, SyncDataFilter<ApplicationInfo>>> filters) {
					if (SyncDataSource.passesAny(theSingleton, filters))
						return Collections.singletonList(theSingleton);
					else
						return Collections.emptyList();
				}
			});
	}

	public ApplicationInfo getApplicationInfo() {
		return theSingleton;
	}

	public void applicationInfoChanged() {
		theRepo.save(theSingleton);
	}
}
