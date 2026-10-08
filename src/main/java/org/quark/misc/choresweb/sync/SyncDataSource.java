package org.quark.misc.choresweb.sync;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.qommons.json.JsonObject;
import org.quark.misc.choresweb.api.ApiJob;
import org.quark.misc.choresweb.entities.Membership;
import org.quark.misc.choresweb.sync.SyncService.SyncDataEvent;

import jakarta.persistence.Id;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

public interface SyncDataSource<U, T> {
	interface SyncDataFilterType<T> {
		public SyncDataSource.SyncDataFilter<T> parseFilter(Object jsonFilter) throws ParseException;
	}

	interface SyncDataFilter<T> {
		boolean filter(T entity);
	}

	public static <T> boolean passesAll(T entity, Map<String, SyncDataFilter<T>> filters) {
		return filters.values().stream().allMatch(filter -> filter.filter(entity));
	}

	public static <T> boolean passesAny(T entity, Iterable<Map<String, SyncDataFilter<T>>> filters) {
		for (var filter : filters) {
			if (passesAll(entity, filter))
				return true;
		}
		return false;
	}

	static class ConstValueFilter<T, F> implements SyncDataFilter<T> {
		private final Function<? super T, ? extends F> theGetter;
		private final F theValue;

		public ConstValueFilter(Function<? super T, ? extends F> getter, F value) {
			theGetter = getter;
			theValue = value;
		}

		public F getValue() {
			return theValue;
		}

		@Override
		public boolean filter(T entity) {
			F value = theGetter.apply(entity);
			return Objects.equals(theValue, value);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	public static <T> T getConstantQueryBy(Map<String, ? extends SyncDataFilter<?>> filters, String field) {
		SyncDataFilter<?> filter = filters.get(field);
		if (!(filter instanceof ConstValueFilter))
			return null;
		return ((ConstValueFilter<?, T>) filter).getValue();
	}

	/** Possible results of checking a subscription against a data change event */
	public enum ValidationChange {
		/** The data change does not affect the subscription */
		Ignore,
		/** The event's entity may possibly have been invisible to the subscription previously, but now it is. */
		AddEntity,
		/**
		 * The event's entity may possibly have been visible to the subscription previously, but now is not.<br>
		 * This type should be avoided if possible when the entity was not previously visible to the subscription.
		 */
		RemoveEntity,
		RevokeSubscription;
	}

	interface ValidationMaintainer<U> {
		ValidationChange checkSubscription(SyncDataEvent<U, ?> event);
	}

	String getEntityTypeName();

	Class<T> getEntityType();

	Object serialize(T entity);

	T deserialize(String json) throws ParseException;

	Map<String, SyncDataSource.SyncDataFilterType<T>> getFilterTypes();

	/**
	 * @param user The user requesting the subscription
	 * @param filters The filters requested by the user, by type name
	 * @return A function to be called for every entity event that occurs in the service to ensure the subscription remains valid.<br>
	 *         When an event does not invalidate this subscription, <code>null</code> should be returned.<br>
	 *         If an event occurs which means the user no longer has access to the data for this entity source and the given filter, the
	 *         function should return a list of the entities that the user no longer has access to.<br>
	 *         These entities will be sent as removals to the client along with the notification that the subscription has been cancelled.
	 * @throws UnsupportedOperationException If the user does not have access to the data from this data source with the given filters.
	 */
	ValidationMaintainer<U> validateSubscription(U user, Map<String, SyncDataSource.SyncDataFilter<T>> filters)
		throws UnsupportedOperationException;

	/**
	 * @param filters The filters to apply
	 * @return All entities in the data set which match any of the given filter combos.<br>
	 *         <b>THIS METHOD MUST NOT FILTER BASED ON PERMISSIONS or any other criterion other than the filters parameter.<b><br>
	 *         The opportunity for permission checks happens in {@link #validateSubscription(Membership, Map)}.
	 */
	Collection<T> queryEntities(List<Map<String, SyncDataSource.SyncDataFilter<T>>> filters);

	@Slf4j
	public static abstract class AbstractReflectedDataSource<U, T> implements SyncDataSource<U, T> {
		private final String theEntityTypeName;
		private final Class<T> theEntityType;
		private final ObjectMapper theObjectMapper;
		private final Map<String, SyncDataFilterType<T>> theFilterTypes;

		public AbstractReflectedDataSource(String entityTypeName, Class<T> entityType, ObjectMapper objectMapper) {
			theEntityTypeName = entityTypeName;
			theEntityType = entityType;
			theObjectMapper = objectMapper;

			Field[] fields = entityType.getDeclaredFields();
			// Discover fields for filtering
			Map<String, SyncDataFilterType<T>> filterTypes = new LinkedHashMap<>();
			for (Field field : fields) {
				if (filterTypes.containsKey(field.getName()))
					continue;
				filterTypes.put(field.getName(), createFilterType(field, theObjectMapper));
			}
			theFilterTypes = Collections.unmodifiableMap(filterTypes);
		}

		private static Field getIdField(Field[] fields) {
			Field idField = findField(fields, f -> f.isAnnotationPresent(Id.class));
			if (idField == null)
				idField = findField(fields, f -> f.getName().equals("id"));
			return idField;
		}

		private static Field findField(Field[] fields, Predicate<Field> filter) {
			for (Field f : fields)
				if (filter.test(f))
					return f;
			return null;
		}

		private static <T, F> SyncDataFilterType<T> createFilterType(Field field, ObjectMapper objectMapper) {
			Class<?> type = field.getType();
			boolean primitive = type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class;
			Field idField = primitive ? null : getIdField(type.getDeclaredFields());
			if (!primitive && idField == null)
				return null;
			field.setAccessible(true);
			if (idField != null)
				idField.setAccessible(true);

			Function<T, ?> simpleGetter = entity -> {
				try {
					return field.get(entity);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new IllegalStateException("Could not retrieve value of field " + field + " for serialization");
				}
			};
			Function<T, F> getter;
			Class<F> fieldType;
			if (primitive) {
				getter = (Function<T, F>) simpleGetter;
				fieldType = (Class<F>) type;
			} else {
				getter = entity -> {
					Object fieldValue = simpleGetter.apply(entity);
					if (fieldValue == null)
						return null;
					try {
						return (F) idField.get(fieldValue);
					} catch (IllegalArgumentException | IllegalAccessException e) {
						throw new IllegalStateException("Could not retrieve value of field " + idField + " for serialization");
					}
				};
				fieldType = (Class<F>) idField.getType();
			}
			return jsonFilterValue -> {
				try {
					Object coercedValue = objectMapper.convertValue(jsonFilterValue, fieldType);
					return new ConstValueFilter<>(getter, coercedValue);
				} catch (RuntimeException e) {
					ParseException x = new ParseException(
						"Failed to parse " + type.getSimpleName() + "." + field.getName() + " (" + fieldType + "): " + e.getMessage(),
						0);
					x.addSuppressed(e);
					throw x;
				}
			};
		}

		@Override
		public String getEntityTypeName() {
			return theEntityTypeName;
		}

		@Override
		public Class<T> getEntityType() {
			return theEntityType;
		}

		@Override
		public Object serialize(T entity) {
			String jsonStr = theObjectMapper.writeValueAsString(entity);
			Map<String, Object> map = theObjectMapper.readValue(jsonStr, Map.class);
			return new JsonObject().withAll(map);
		}

		@Override
		public T deserialize(String json) throws ParseException {
			try {
				return theObjectMapper.readValue(json, theEntityType);
			} catch (RuntimeException e) {
				ParseException x = new ParseException("Could not parse " + theEntityTypeName + " JSON", 0);
				x.addSuppressed(e);
				throw x;
			}
		}

		@Override
		public Map<String, SyncDataFilterType<T>> getFilterTypes() {
			return theFilterTypes;
		}
	}

	public static abstract class SingleFieldValidationDataSource<U, T, ID> extends AbstractReflectedDataSource<U, T> {
		private final String theAuthFieldName;

		public SingleFieldValidationDataSource(String entityTypeName, Class<T> entityType, ObjectMapper objectMapper,
			String authFieldName) {
			super(entityTypeName, entityType, objectMapper);
			theAuthFieldName = authFieldName;
		}

		protected abstract String isAuthorized(U user, ID authFieldValue);

		protected abstract ValidationMaintainer<U> maintainValidation(U user, ID authFieldValue);

		protected abstract Stream<T> getEntities(ID authFieldValue);

		@Override
		public ValidationMaintainer<U> validateSubscription(U user, Map<String, SyncDataFilter<T>> filters)
			throws UnsupportedOperationException {
			SyncDataFilter<T> orgFilter = filters.get(theAuthFieldName);
			if (orgFilter == null)
				throw new UnsupportedOperationException("Query requires a filter for " + theAuthFieldName);
			else if (!(orgFilter instanceof SyncDataSource.ConstValueFilter))
				throw new UnsupportedOperationException(theAuthFieldName + " filter must be a constant value");
			ID authFieldValue = ((SyncDataSource.ConstValueFilter<ApiJob, ID>) orgFilter).getValue();
			String authFailed = isAuthorized(user, authFieldValue);
			if (authFailed != null)
				throw new NoSuchElementException(authFailed);
			return maintainValidation(user, authFieldValue);
		}

		@Override
		public List<T> queryEntities(List<Map<String, SyncDataFilter<T>>> filters) {
			// The filters should have already passed validation above
			if (filters.size() == 1) {
				ID authFieldValue = ((SyncDataSource.ConstValueFilter<T, ID>) filters.get(0).get(theAuthFieldName)).getValue();
				return getEntities(authFieldValue)//
					.filter(job -> SyncDataSource.passesAll(job, filters.get(0)))//
					.toList();
			} else {
				Map<ID, List<Map<String, SyncDataFilter<T>>>> filtersByAuth = new HashMap<>();
				for (Map<String, SyncDataFilter<T>> filter : filters) {
					ID authFieldValue = ((SyncDataSource.ConstValueFilter<T, ID>) filters.get(0).get(theAuthFieldName)).getValue();
					var valueFilters = filtersByAuth.computeIfAbsent(authFieldValue, _ -> new ArrayList<>());
					if (filter.size() > 1)
						valueFilters.add(filter);
				}
				List<T> entities = new ArrayList<>();
				for (Map.Entry<ID, List<Map<String, SyncDataFilter<T>>>> authValue : filtersByAuth.entrySet()) {
					Stream<T> authValues = getEntities(authValue.getKey());
					if (!authValue.getValue().isEmpty())
						authValues = authValues.filter(job -> SyncDataSource.passesAny(job, authValue.getValue()));
					authValues.forEach(entities::add);
				}
				return entities;
			}
		}
	}
}