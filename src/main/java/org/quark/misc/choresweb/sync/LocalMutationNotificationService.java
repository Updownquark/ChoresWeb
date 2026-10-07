package org.quark.misc.choresweb.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.qommons.ArrayUtils;
import org.qommons.collect.BetterList;
import org.qommons.collect.CircularArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(name = "quark.sync.scaling.enabled", havingValue = "false", matchIfMissing = true)
public class LocalMutationNotificationService extends EntityMutationNotificationService {
	private final CircularArrayList<EntityMutationEvent> theEventCache;

	public LocalMutationNotificationService(ApplicationEventPublisher internalEventPublisher, //
		@Value("${quark.sync.scaling.max-events}") int maxEvents) {
		super(internalEventPublisher);
		theEventCache = CircularArrayList.build()//
			.withMaxCapacity(maxEvents)//
			.build();
	}

	@Override
	protected synchronized Mono<MessageId> publishGlobalMutation(String entityType, boolean present, String entityJson) {
		MessageId messageId = MessageId.generate();
		EntityMutationEvent event = new EntityMutationEvent(messageId, entityType, present, entityJson);
		theEventCache.add(event);
		publishLocalMutation(event);
		return Mono.just(messageId);
	}

	@Override
	public Mono<Optional<MessageId>> getLatestMutation() {
		return Mono.fromCallable(() -> {
			synchronized (this) {
				EntityMutationEvent message = theEventCache.peekLast();
				return message == null ? Optional.empty() : Optional.of(message.getId());
			}
		});
	}

	@Override
	public Mono<Optional<MessageId>> getOldestCachedMutation() {
		return Mono.fromCallable(() -> {
			synchronized (this) {
				EntityMutationEvent message = theEventCache.peekFirst();
				return message == null ? Optional.empty() : Optional.of(message.getId());
			}
		});
	}

	@Override
	public Flux<EntityMutationEvent> getCachedMutations(MessageId after) {
		return Flux.defer(() -> {
			synchronized (this) {
				if (after == null)
					return Flux.fromIterable(BetterList.of(theEventCache)); // Immutable copy
				int index = ArrayUtils.binarySearch(theEventCache, evt -> after.compareTo(evt.getId()));
				if (index < 0)
					index = -index - 1;
				else
					index++;
				List<EntityMutationEvent> rangeCopy = new ArrayList<>(theEventCache.size() - index);
				rangeCopy.addAll(theEventCache.subList(index, theEventCache.size()));
				return Flux.fromStream(rangeCopy.stream());
			}
		});
	}
}
