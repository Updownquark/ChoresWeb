package org.quark.misc.choresweb.sync;

public class EntityMutationEvent {
	private final MessageId theId;
	private final String theEntityTypeName;
	private final boolean isPresent;
	private final String theEntityJson;

	public EntityMutationEvent(MessageId id, String entityTypeName, boolean isPresent, String entityJson) {
		theId = id;
		theEntityTypeName = entityTypeName;
		this.isPresent = isPresent;
		theEntityJson = entityJson;
	}

	public MessageId getId() {
		return theId;
	}

	public String getEntityTypeName() {
		return theEntityTypeName;
	}

	public boolean isPresent() {
		return isPresent;
	}

	public String getEntityJson() {
		return theEntityJson;
	}
}
