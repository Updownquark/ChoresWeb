package org.quark.misc.choresweb.api;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

public class ChoresApplicationEvent extends ApplicationEvent {
	public enum Type {
		DATA_CHANGE, SECURITY_MUTATION
	}

	// Universal fields
	@Getter
	private final Type eventType;
	@Getter
	private final long organization;
	@Getter
	private final long eventTime;
	/**
	 * For data change events, this signifies whether the modification represents an add or update (true) versus a deletion (false).<br>
	 * For security events, this signifies whether the affected user is a member of the organization.
	 */
	@Getter
	private final boolean exists;

	// Data fields (used when eventType == DATA_CHANGE)
	@Getter
	private final String tableName;
	@Getter
	private final Long entityId;

	// Security fields (used when eventType == SECURITY_MUTATION)
	@Getter
	private final Long affectedUserId;

	/** Creates a data change event */
	public static ChoresApplicationEvent dataChange(Object source, long organization, long eventTime, //
		String tableName, long entityId, boolean exists) {
		return new ChoresApplicationEvent(source, Type.DATA_CHANGE, organization, eventTime, exists, //
			tableName, entityId, //
			null);
	}

	/** Creates a security change event */
	public static ChoresApplicationEvent securityMutation(Object source, long organization, long eventTime, //
		long affectedUserId, boolean member) {
		return new ChoresApplicationEvent(source, Type.SECURITY_MUTATION, organization, eventTime, member, //
			null, null, //
			affectedUserId);
	}

	// Private master constructor
	private ChoresApplicationEvent(//
		Object source, Type eventType, long organization, long eventTime, boolean exists, // Universal fields
		String tableName, Long entityId, // Application event fields
		Long affectedUserId // Security event fields
	) {
		super(source);
		this.eventType = eventType;
		this.organization = organization;
		this.exists = exists;
		this.tableName = tableName;
		this.entityId = entityId;
		this.affectedUserId = affectedUserId;
		this.eventTime = eventTime;
	}

	public ClientEvent toClientEvent() {
		switch (eventType) {
		case SECURITY_MUTATION:
			return new ClientEvent("organization", organization, exists, eventTime);
		default:
			return new ClientEvent(tableName, entityId, exists, eventTime);
		}
	}

	public static class ClientEvent {
		@Getter
		private final String tableName;
		@Getter
		private final long entityId;
		@Getter
		private final boolean exists;
		@Getter
		private final long eventTime;

		public ClientEvent(String tableName, long entityId, boolean exists, long eventTime) {
			this.tableName = tableName;
			this.entityId = entityId;
			this.exists = exists;
			this.eventTime = eventTime;
		}

	}
}
