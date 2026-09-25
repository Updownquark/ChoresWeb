package org.quark.misc.choresweb.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.QommonsUtils;

public class EntityChangeSet<ID, E> {
	private final NavigableMap<Long, E> theChanges;
	private final Map<ID, Long> theEntityChangeTimes;
	private final Function<? super E, ? extends ID> idGetter;
	private long theBufferDuration;
	private long theLastDeleted;

	public EntityChangeSet(Function<? super E, ? extends ID> idGetter, long bufferDurationMillis) {
		theChanges = new TreeMap<>();
		theEntityChangeTimes = new HashMap<>();
		this.idGetter = idGetter;
		theBufferDuration = bufferDurationMillis * 1_000_000L;
		theLastDeleted = System.nanoTime() - 1;
	}

	public synchronized long getLastChangeTime() {
		if (theChanges.isEmpty())
			return theLastDeleted;
		else
			return theChanges.lastKey();
	}

	public synchronized void changed(E change) {
		ID id = idGetter.apply(change);
		Long now = System.nanoTime();
		long lastChange = getLastChangeTime();
		if (now == lastChange)
			now = lastChange + 1;
		Long lastEntityChange = theEntityChangeTimes.put(id, now);
		if (lastEntityChange != null)
			theChanges.remove(lastEntityChange);
		Long oldest = now - theBufferDuration;
		NavigableMap<Long, E> toDelete = theChanges.headMap(oldest, false);
		if (!toDelete.isEmpty()) {
			theLastDeleted = toDelete.lastKey();
			for (E entity : toDelete.values())
				theEntityChangeTimes.remove(idGetter.apply(entity));
			toDelete.clear();
		}
		theChanges.put(now, change);
	}

	public synchronized <C> ChangeSet<C> getChanges(long lastKnown, Predicate<? super E> filter, Function<? super E, ? extends C> map) {
		if (lastKnown < theLastDeleted)
			return null;
		NavigableMap<Long, E> changes = theChanges.tailMap(lastKnown, false);
		if (changes.isEmpty())
			return new ChangeSet<>(lastKnown, Collections.emptyList());
		else
			return new ChangeSet<>(changes.lastKey(), QommonsUtils.filterMap(changes.values(), filter, map));
	}

	public synchronized <V> ChangeSet<V> getValues(Supplier<List<V>> values) {
		return new ChangeSet<>(getLastChangeTime(), values.get());
	}

	public static record ChangeSet<E>(long lastTime, List<E> changes) {}
}
