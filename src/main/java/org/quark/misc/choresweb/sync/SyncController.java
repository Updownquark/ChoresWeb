package org.quark.misc.choresweb.sync;

import java.util.Collections;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/sync")
public abstract class SyncController<U> {
	private final SyncService<U> theSyncService;

	public SyncController(SyncService<U> syncService) {
		theSyncService = syncService;

		theSyncService.setUserIdentity(this::getUserId);
	}

	protected abstract U getMe(Jwt user);

	protected abstract Object getUserId(U user);

	@GetMapping(value = "/init", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> initStream(//
		@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = false) String subscriptions, //
		@RequestParam(required = false) String lastEventId) {
		U me = getMe(user);
		return theSyncService.initStream(me, lastEventId, subscriptions);
	}

	@PostMapping("/modify")
	public List<String> modifySubscriptions(//
		@AuthenticationPrincipal Jwt user, //
		@RequestParam(required = true) String streamId, //
		@RequestParam(required = false) List<String> unsubscribe, //
		@RequestParam(required = false) String subscribe, //
		@RequestParam(required = false) Boolean withInitialData) {
		if (unsubscribe == null && subscribe == null)
			return Collections.emptyList();
		U me = getMe(user);
		return theSyncService.modifySubscriptions(me, streamId, unsubscribe, subscribe, Boolean.TRUE.equals(withInitialData));
	}
}
