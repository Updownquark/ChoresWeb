package org.quark.misc.choresweb.ctl;

import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.TimeUtils;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.ListenerList;
import org.qommons.threading.QommonsTimer;
import org.quark.misc.choresweb.api.ChoresApplicationEvent;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.entities.User;
import org.quark.misc.choresweb.svc.OrganizationService;
import org.quark.misc.choresweb.svc.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
	private static final Duration BATCH_TIME = Duration.ofMillis(50);

	private final UserService theUserService;
	private final OrganizationService theOrgsService;

	private final Map<Long, OrgSseSessions> theOrgSessions = new ConcurrentHashMap<>();
	private final Map<Long, UserSseSessions> theGlobalSessions = new ConcurrentHashMap<>();
	private long theBatchTime;
	private final CircularArrayList<EventBatch> theOrgSpecificEventCache = CircularArrayList.build()//
		.withInitCapacity(1000)//
		.build();
	private final CircularArrayList<EventBatch> theGlobalOrgEventCache = CircularArrayList.build()//
		.withInitCapacity(1000)//
		.build();
	private final long theEventCacheTimeout;
	private final Map<Long, ListenerList<ChoresApplicationEvent>> theEventBatching = new ConcurrentHashMap<>();

	public SyncController(UserService userService, OrganizationService orgService,
		@Value("${chores.sync.cacheTimeout}") String cacheTimeoutStr) {
		theUserService = userService;
		theOrgsService = orgService;
		try {
			theEventCacheTimeout = TimeUtils.parseDuration(cacheTimeoutStr).asDuration().toMillis();
		} catch (ParseException e) {
			throw new IllegalStateException("Bad cache timoue: " + cacheTimeoutStr);
		}
	}

	@GetMapping(value = "/orgs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = false) Long lastSync) {
		User me = theUserService.getUserCreateIfAdmin(UserService.getUserEmail(user));

		// Do it this way for thread safety
		SseEmitter[] emitter = new SseEmitter[1];
		Long userId = me.getId();
		theGlobalSessions.compute(userId, (_, sessions) -> {
			if (sessions == null) {
				sessions = new UserSseSessions(null, userId);
			}

			List<ChoresApplicationEvent> missedEvents = lastSync == null ? null : getMissedEvents(lastSync);

			sessions.createSession(30 * 60 * 1000, missedEvents, emitter);
			return sessions;
		});
		return emitter[0];
	}

	@GetMapping(value = "/org-changes/{orgId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@AuthenticationPrincipal Jwt user, //
		@PathVariable(required = true) long orgId, //
		@RequestParam(required = false) Long lastSync) {
		Membership me = theOrgsService.getMe(user, orgId); // Throws an exception if not a member, i.e. if they don't have access

		List<ChoresApplicationEvent> missedEvents = lastSync == null ? null : getMissedEvents(lastSync, theOrgSpecificEventCache);

		// Do it this way for thread safety
		SseEmitter[] emitter = new SseEmitter[1];
		theOrgSessions.compute(orgId, (_, sessions) -> {
			if (sessions == null) {
				sessions = new OrgSseSessions(orgId);
			}
			sessions.createSession(me.getMember().getId(), 30 * 60 * 1000, missedEvents, emitter);
			return sessions;
		});
		return emitter[0];
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleChoresEvent(ChoresApplicationEvent event) {
		if (event.getEventType() == ChoresApplicationEvent.Type.SECURITY_MUTATION) {
			// Security changes need to happen only at the end of a batch
			List<EventBatch> batch = dumpAndClearBatch(event);
			if (batch != null)
				QommonsTimer.getCommonInstance().offload(() -> handleEventBatch(batch));
		} else {
			addToBatch(event);
			QommonsTimer.getCommonInstance().doAfterInactivity("choresSyncBatch", () -> {
				List<EventBatch> batch = dumpAndClearBatch(null);
				if (batch != null)
					handleEventBatch(batch);
			}, BATCH_TIME);
		}
	}

	private void addToBatch(ChoresApplicationEvent event) {
		theEventBatching.compute(event.getOrganization(), (_, current) -> {
			if (current == null)
				current = ListenerList.build().build();
			current.add(event, false);
			return current;
		});
	}

	private synchronized long getBatchTime(long now) {
		if (now <= theBatchTime)
			now = theBatchTime + 1;
		theBatchTime = now;
		return now;
	}

	private List<EventBatch> dumpAndClearBatch(ChoresApplicationEvent securityEvent) {
		Map<Long, List<ChoresApplicationEvent>> events = null;
		for (Long orgId : theEventBatching.keySet()) {
			List<ChoresApplicationEvent> orgEvents = theEventBatching.remove(orgId).dump();
			if (events == null)
				events = new HashMap<>();
			events.put(orgId, orgEvents);
		}

		List<EventBatch> batches = new ArrayList<>(events.size());
		long batchTime = getBatchTime(System.currentTimeMillis());
		boolean first;
		boolean foundSecurity = securityEvent == null;
		if (events != null || securityEvent != null) {
			if (events == null)
				events = new HashMap<>();
			for (Map.Entry<Long, List<ChoresApplicationEvent>> orgEvents : events.entrySet()) {
				if (first)
					first = false;
				else
					batchTime = getBatchTime(batchTime + 1);
				EventBatch batch = new EventBatch(orgEvents.getKey(), batchTime, orgEvents.getValue());
				if (!foundSecurity && securityEvent.getOrganization() == orgEvents.getKey().longValue()) {
					foundSecurity = true;
					batch.securityEvent = securityEvent;
				}
				batches.add(batch);
			}
		}
		if (!foundSecurity) {
			EventBatch batch = new EventBatch(-1, first ? batchTime : getBatchTime(batchTime), Collections.emptyList());
			batch.securityEvent = securityEvent;
			batches.add(batch);
		}
		return batches;
	}

	private void handleEventBatch(List<EventBatch> events) {
		Set<Long> addedOrUpdatedOrgs = null;
		Set<Long> removedOrgs = null;

		for (EventBatch orgEvents : events) {
			// Do it this way to prevent cached events from being missed
			theOrgSessions.computeIfPresent(orgEvents.orgId, (_, orgSessions) -> {
				cacheEventBatch(orgEvents, theOrgSpecificEventCache, theEventCacheTimeout);
				for (Map.Entry<EventTypeKey, List<Long>> typeEvents : orgEvents.events.entrySet()) {
					if ("organization".equals(typeEvents.getKey().tableName)) {
						if (typeEvents.getKey().exists) {
							if (addedOrUpdatedOrgs == null)
								addedOrUpdatedOrgs = new LinkedHashSet<>();
							addedOrUpdatedOrgs.addAll(typeEvents.getValue());
						} else {
							if (addedOrUpdatedOrgs != null)
								addedOrUpdatedOrgs.removeAll(typeEvents.getValue());
							if (removedOrgs == null)
								removedOrgs = new LinkedHashSet<>();
							removedOrgs.addAll(typeEvents.getValue());
						}
					}
					orgSessions.fireDataEvent(typeEvents.getKey().tableName, typeEvents.getKey().exists, typeEvents.getValue());
				}
				if (orgEvents.securityEvent != null && !orgEvents.securityEvent.isExists()) {
					// A user's membership in an organization has been revoked
					orgSessions.deleteUser(orgEvents.securityEvent.getAffectedUserId());
				}
				return orgSessions;
			});
		}

		if (addedOrUpdatedOrgs != null || removedOrgs != null) {
			List<ChoresApplicationEvent> orgEvents = new ArrayList<>(//
				(addedOrUpdatedOrgs == null ? 0 : addedOrUpdatedOrgs.size())//
					+ (removedOrgs == null ? 0 : removedOrgs.size()));
			if (addedOrUpdatedOrgs != null)
				orgEvents.addAll(addedOrUpdatedOrgs.values());
			if (removedOrgs != null)
				orgEvents.addAll(removedOrgs.values());
			Collections.sort(orgEvents, (e1, e2) -> Long.compare(e1.getEventTime(), e2.getEventTime()));
			cacheEventBatch(new EventBatch(-1, getBatchTime(System.currentTimeMillis()), orgEvents), theGlobalOrgEventCache,
				theEventCacheTimeout);
		}
	}

	private static void cacheEventBatch(EventBatch events, CircularArrayList<EventBatch> cache, long timeout) {
		synchronized (cache) {
			long expireTime = System.currentTimeMillis() - timeout;
			int expireIndex = ArrayUtils.binarySearch(cache, evt -> Long.compare(expireTime, evt.batchTime));
			// Note: we don't care whether an event exactly matching the expire time is purged or not. Just not worth thinking about.
			if (expireIndex < 0)
				expireIndex = -expireIndex - 1;
			/* Leave one expired event in the cache.
			 * This costs very little memory, and it allows us to tell clients that nothing has happened if they're up-to-date
			 * but all events in the cache have expired.
			 */
			expireIndex--;
			if (expireIndex >= 0)
				cache.removeRange(0, expireIndex);

			int addIndex = cache.size();
			while (addIndex > 0 && cache.get(addIndex - 1).batchTime > events.batchTime)
				addIndex--;
			cache.add(addIndex, events);
		}
	}

	private static List<ChoresApplicationEvent> getMissedEvents(long lastSyncTime, List<EventBatch> cache) {
		synchronized (cache) {
			int index = ArrayUtils.binarySearch(cache, evt -> Long.compare(lastSyncTime, evt.batchTime));
			if (index < 0) {
				// We don't purge events in the middle of the cache, so the lack of an event with the given time
				// means the client can't be updated event-wise and they'll need to re-synchronize via the API.
				return null;
			}
			// Copy missed events
			return QommonsUtils.filterMap(cache.subList(index + 1, cache.size()), null, null);
		}
	}

	void deleteOrg(OrgSseSessions org) {
		theOrgSessions.computeIfPresent(org.getOrgId(), (_, current) -> {
			if (current == org && current.isEmpty())
				return null;
			else
				return current;
		});
	}

	void deleteGlobal(UserSseSessions user) {
		theGlobalSessions.computeIfPresent(user.getUserId(), (_, current) -> {
			if (current == user && current.isEmpty())
				return null;
			else
				return current;
		});
	}

	static class EventBatch {
		final long orgId;
		final long batchTime;
		final Map<EventTypeKey, List<Long>> events;
		ChoresApplicationEvent securityEvent;

		EventBatch(long orgId, long batchTime, Collection<ChoresApplicationEvent> eventList) {
			this.orgId = orgId;
			this.batchTime = batchTime;
			this.events = new HashMap<>();

			for (ChoresApplicationEvent event : eventList) {
				events.computeIfAbsent(new EventTypeKey(event.getTableName(), event.isExists()), _ -> new ArrayList<>())
					.add(event.getEntityId());
				if (event.isExists())
					events.remove(new EventTypeKey(event.getTableName(), true));
			}
		};
	}

	static class EventTypeKey {
		final String tableName;
		final boolean exists;

		EventTypeKey(String tableName, boolean exists) {
			this.tableName = tableName;
			this.exists = exists;
		}

		@Override
		public int hashCode() {
			int hash = tableName.hashCode();
			if (exists)
				return hash;
			else
				return -hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof EventTypeKey))
				return false;
			return tableName.equals(((EventTypeKey) obj).tableName) && exists == ((EventTypeKey) obj).exists;
		}

		@Override
		public String toString() {
			return tableName + "(" + exists + ")";
		}
	}

	class OrgSseSessions {
		private final Long theOrgId;
		private final Map<Long, UserSseSessions> theUserSessions;

		public OrgSseSessions(Long userId) {
			theOrgId = userId;
			theUserSessions = new ConcurrentHashMap<>();
		}

		Long getOrgId() {
			return theOrgId;
		}

		boolean isEmpty() {
			return theUserSessions.isEmpty();
		}

		public void createSession(Long userId, long timeout, List<ChoresApplicationEvent> missedEvents, SseEmitter[] emitter) {
			theUserSessions.compute(userId, (_, current) -> {
				if (current == null)
					current = new UserSseSessions(this, userId);
				current.createSession(timeout, missedEvents, emitter);
				return current;
			});
		}

		void deleteUser(UserSseSessions user) {
			if (null == theUserSessions.computeIfPresent(user.getUserId(), (_, currentSessions) -> {
				if (currentSessions == user && user.isEmpty())
					return null;
				else
					return currentSessions;
			}) && theUserSessions.isEmpty())
				SyncController.this.deleteOrg(this);
		}

		void deleteUser(Long userId) {
			theUserSessions.computeIfPresent(userId, (_, current) -> {
				current.clear();
				return null;
			});
		}

		void fireDataEvent(ChoresApplicationEvent.ClientEvent event) {
			theUserSessions.values().forEach(session -> session.fireDataEvent(event));
		}
	}

	class UserSseSessions {
		private final OrgSseSessions theOrgSessions;
		private final Long theUserId;
		private final Map<String, SseEmitter> theSessions;

		UserSseSessions(OrgSseSessions orgSessions, Long userId) {
			theOrgSessions = orgSessions;
			theUserId = userId;
			theSessions = new ConcurrentHashMap<>();
		}

		Long getUserId() {
			return theUserId;
		}

		boolean isEmpty() {
			return theSessions.isEmpty();
		}

		void createSession(long timeout, List<ChoresApplicationEvent> missedEvents, SseEmitter[] emitter) {
			SseEmitter newEmitter = new SseEmitter(timeout);
			String sessionKey = QommonsUtils.getRandomString(100); // Should never clash
			theSessions.put(sessionKey, newEmitter);

			newEmitter.onCompletion(() -> deleteSession(sessionKey, null));
			newEmitter.onTimeout(() -> deleteSession(sessionKey, null));
			newEmitter.onError(_ -> deleteSession(sessionKey, null));

			// Send dummy handshake so client finishes connection instantly
			String initData;
			if (missedEvents == null)
				initData = "{\"upToDate\": false, \"lastChangeTime\": " + theBatchTime + "}";
			else
				initData = "{\"upToDate\": true}"; // They know the last change time already
			try {
				newEmitter.send(SseEmitter.event().name("init").data(initData));
			} catch (IOException e) {
				deleteSession(sessionKey, e);
			}

			if (missedEvents != null) {
				for (ChoresApplicationEvent event : missedEvents) {
					boolean orgMatch;
					if (theOrgSessions != null)
						orgMatch = event.getOrganization() == theOrgSessions.getOrgId().longValue();
					else
						orgMatch = true;
					if (orgMatch) {
						if (event.getEventType() == ChoresApplicationEvent.Type.SECURITY_MUTATION) {
							if (event.getAffectedUserId() == theUserId.longValue())
								event = ChoresApplicationEvent.dataChange(SyncController.this, event.getOrganization(),
									event.getEventTime(), "organization", event.getOrganization(), event.isExists());
						} else if (theOrgSessions == null && !"organization".equals(event.getTableName()))
							continue;
						try {
							newEmitter.send(//
								SseEmitter.event()//
									.name("data-change")//
									.data(event.toClientEvent(), MediaType.APPLICATION_JSON));
						} catch (IOException e) {
							deleteSession(sessionKey, e);
							break;
						}
					}
				}
			}

			emitter[0] = newEmitter;
		}

		void deleteSession(String sessionKey, Throwable error) {
			SseEmitter emitter = theSessions.remove(sessionKey);
			if (emitter != null) {
				if (error != null)
					emitter.completeWithError(error);
				if (theSessions.isEmpty()) {
					if (theOrgSessions != null)
						theOrgSessions.deleteUser(this);
					else
						deleteGlobal(this);
				}
			}
		}

		void clear() {
			theSessions.values().forEach(SseEmitter::complete);
			theSessions.clear();
			if (theOrgSessions != null)
				theOrgSessions.deleteUser(this);
			else
				deleteGlobal(this);
		}

		void fireDataEvent(ChoresApplicationEvent.ClientEvent event) {
			theSessions.forEach((sessionKey, emitter) -> {
				try {
					emitter.send(//
						SseEmitter.event()//
							.name("data-change")//
							.data(event, MediaType.APPLICATION_JSON));
				} catch (IOException e) {
					deleteSession(sessionKey, e);
				}
			});
		}
	}
}
