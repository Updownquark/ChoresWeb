package org.quark.misc.choresweb.sync;

public class MessageId implements Comparable<MessageId> {
	public final long timeStamp;
	public final int sequence;
	private final String theString;

	public MessageId(String messageId) {
		theString = messageId;
		int dash = messageId.lastIndexOf('-'); // Search from the end for performance
		this.timeStamp = Long.parseLong(messageId.substring(0, dash));
		this.sequence = Integer.parseInt(messageId.substring(dash + 1));
	}

	public MessageId(long timeStamp, int sequence) {
		this.timeStamp = timeStamp;
		this.sequence = sequence;
		theString = new StringBuilder().append(timeStamp).append('-').append(sequence).toString();
	}

	@Override
	public int compareTo(MessageId other) {
		int cmp = Long.compare(this.timeStamp, other.timeStamp);
		if (cmp != 0)
			return cmp;
		return Integer.compare(this.sequence, other.sequence);
	}

	@Override
	public String toString() {
		return theString;
	}

	private static class Generator {
		private long currentTime;
		private int sequence;

		synchronized MessageId get() {
			long now = System.currentTimeMillis();
			int seq;
			if (now == currentTime)
				sequence = seq = sequence + 1;
			else {
				currentTime = now;
				sequence = seq = 0;
			}
			return new MessageId(now, seq);
		}
	}
	private static final Generator GEN = new Generator();

	public static MessageId generate() {
		return GEN.get();
	}
}
