package org.quark.misc.choresweb.sync;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.qommons.fn.FunctionUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

public abstract class EntityMutationNotificationService {
	public interface EntitySerializer<E>{
		/**
		 * @param entity The entity to serialize
		 * @param idOnly Whether to serialize only the ID fields of the entity
		 * @return The JSON-serialized entity
		 */
		String serialize(E entity);
	}

	@Slf4j
	public static class ReflectiveSerializer<T> implements EntitySerializer<T> {
		private final ObjectMapper theObjectMapper;

		public ReflectiveSerializer(Class<T> type, ObjectMapper objectMapper) {
			theObjectMapper = objectMapper;
		}

		@Override
		public String serialize(T entity) {
			return theObjectMapper.writeValueAsString(entity);
		}
	}

	private static class EntitySerializerHolder<E> {
		final Class<E> entityType;
		final EntitySerializer<E> serializer;

		EntitySerializerHolder(Class<E> entityType, EntitySerializer<E> serializer) {
			this.entityType = entityType;
			this.serializer = serializer;
		}
	}

	private static class InternalDataMutationEvent extends ApplicationEvent {
		final String entityType;
		final boolean present;
		final String entityJson;
		final Sinks.One<Mono<MessageId>> messageId;

		InternalDataMutationEvent(Object source, String entityType, boolean present, String entityJson) {
			super(source);
			this.entityType = entityType;
			this.present = present;
			this.entityJson = entityJson;
			messageId = Sinks.one();
		}
	}

	private final ApplicationEventPublisher theInternalEventPublisher;

	private final Map<String, EntitySerializerHolder<?>> theSerializers = new ConcurrentHashMap<>();
	private final Sinks.Many<EntityMutationEvent> theLocalSink = Sinks.many().multicast().onBackpressureBuffer();

	protected EntityMutationNotificationService(ApplicationEventPublisher internalEventPublisher) {
		theInternalEventPublisher = internalEventPublisher;
	}

	public <E> void installSerializer(String entityTypeName, Class<E> entityType, EntitySerializer<E> serializer) {
		theSerializers.put(entityTypeName, new EntitySerializerHolder<>(entityType, serializer));
	}

	public <E> Mono<MessageId> publishMutation(String entityTypeName, boolean present, E entity) {
		EntitySerializerHolder<E> serializer = (EntitySerializerHolder<E>) theSerializers.get(entityTypeName);
		if (serializer == null)
			throw new IllegalArgumentException("No serializer installed for entity type " + entityTypeName);
		else if (!serializer.entityType.isInstance(entity))
			throw new IllegalArgumentException(
				"Given entity is not an instance of " + entityTypeName + " (" + serializer.entityType.getName() + ")");
		String entityJson = serializer.serializer.serialize(entity);
		InternalDataMutationEvent internalEvent = new InternalDataMutationEvent(this, entityTypeName, present, entityJson);
		theInternalEventPublisher.publishEvent(internalEvent);
		return internalEvent.messageId.asMono().flatMap(FunctionUtils.identity());
	}

	public Flux<EntityMutationEvent> mutations() {
		return theLocalSink.asFlux();
	}

	public abstract Mono<Optional<MessageId>> getLatestMutation();

	public abstract Mono<Optional<MessageId>> getOldestCachedMutation();

	/**
	 * @param after The message ID to get mutations after, or null to get all cached mutations
	 * @return All events in the cache after the given message
	 */
	public abstract Flux<EntityMutationEvent> getCachedMutations(MessageId after);

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	private void publishInternalMutation(InternalDataMutationEvent event) {
		event.messageId.tryEmitValue(publishGlobalMutation(event.entityType, event.present, event.entityJson));
	}

	protected abstract Mono<MessageId> publishGlobalMutation(String entityType, boolean present, String entityJson);

	protected void publishLocalMutation(EntityMutationEvent event) {
		theLocalSink.tryEmitNext(event);
	}
}
