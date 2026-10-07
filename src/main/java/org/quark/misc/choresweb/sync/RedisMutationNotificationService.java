package org.quark.misc.choresweb.sync;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@ConditionalOnProperty(name = "sync.scaling.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class RedisMutationNotificationService extends EntityMutationNotificationService {
	private final String theStreamKey;
	private final int theMaxAllowedEvents;
	private final XAddOptions theRedisPurgeOptions;

	private final ReactiveRedisTemplate<String, String> theRedisTemplate;

	public RedisMutationNotificationService(ReactiveRedisTemplate<String, String> redisTemplate,
		ApplicationEventPublisher internalEventPublisher, //
		@Value("${quark.sync.scaling.stream-key}") String streamKey, //
		@Value("${quark.sync.scaling.max-events}") int maxEvents) {
		super(internalEventPublisher);
		theRedisTemplate = redisTemplate;
		theStreamKey = streamKey;
		theMaxAllowedEvents = maxEvents;
		theRedisPurgeOptions = XAddOptions.trim(TrimOptions.maxLen(theMaxAllowedEvents).approximate());

		startBackgroundStreamReader();
	}

	@Override
	protected Mono<MessageId> publishGlobalMutation(String entityType, boolean present, String entityJson) {
		Map<String, String> fields = new LinkedHashMap<>(6);
		fields.put("entityType", entityType);
		fields.put("present", Boolean.toString(present));
		fields.put("entity", entityJson);

		ObjectRecord<String, Map<String, String>> record = StreamRecords.newRecord()//
			.in(theStreamKey)//
			.ofObject(fields);

		Mono<String> redisMessageId = theRedisTemplate.opsForStream()//
			.add(record, theRedisPurgeOptions)//
			.map(recordId -> recordId.getValue());
		return redisMessageId.map(MessageId::new);
	}

	@Override
	public Mono<Optional<MessageId>> getLatestMutation() {
		return theRedisTemplate.opsForStream()//
			.read(StreamReadOptions.empty().count(1), StreamOffset.create(theStreamKey, ReadOffset.latest()))//
			.next()//
			.map(record -> Optional.of(new MessageId(record.getId().getValue())))//
			.defaultIfEmpty(Optional.empty());
	}

	@Override
	public Mono<Optional<MessageId>> getOldestCachedMutation() {
		return theRedisTemplate.opsForStream()//
			.read(StreamReadOptions.empty().count(1), StreamOffset.create(theStreamKey, ReadOffset.from("0-0")))//
			.next()//
			.map(record -> Optional.of(new MessageId(record.getId().getValue())))//
			.defaultIfEmpty(Optional.empty());
	}

	@Override
	public Flux<EntityMutationEvent> getCachedMutations(MessageId after) {
		ReadOffset readOffset = (after == null) ? ReadOffset.from("0-0") : ReadOffset.from(after.toString());
		return theRedisTemplate.opsForStream()//
			.read(StreamReadOptions.empty().count(theMaxAllowedEvents), //
				StreamOffset.create(theStreamKey, readOffset))//
			.map(record -> parseEvent(record.getId().getValue(), record.getValue()));
	}

	private void startBackgroundStreamReader() {
		// Configure the receiver to block natively for 2 seconds waiting for new items
		StreamReceiverOptions<String, MapRecord<String, String, String>> options = StreamReceiverOptions.builder()
			.pollTimeout(Duration.ofSeconds(2)).build();

		// Create the receiver using the existing ConnectionFactory
		StreamReceiver<String, MapRecord<String, String, String>> receiver = StreamReceiver.create(theRedisTemplate.getConnectionFactory(),
			options);

		// There are no connections currently, so start reading only new messages
		StreamOffset<String> liveOffset = StreamOffset.create(theStreamKey, ReadOffset.latest());
		receiver.receive(liveOffset)//
		.publishOn(Schedulers.boundedElastic())//
		.map(record -> parseEvent(record.getId().getValue(), record.getValue()))//
		// Stream continuously, pumping payloads directly into your live multicast channel
		.doOnNext(this::publishLocalMutation)//
		.doOnError(err -> log.error("Background stream reader encountered an error", err))//
		// Automatically re-subscribe if a network connection drop breaks the loop
		.retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)))//
		.subscribe(); // Activates the background listener context
	}

	private static EntityMutationEvent parseEvent(String messageId, Map<?, ?> fields) {
		return new EntityMutationEvent(new MessageId(messageId), //
			(String) fields.get("entityType"), //
			!"false".equals(fields.get("present")), //
			(String) fields.get("entity"));
	}
}
