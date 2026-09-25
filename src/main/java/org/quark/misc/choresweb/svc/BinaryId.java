package org.quark.misc.choresweb.svc;

public class BinaryId {
	public final long id1;
	public final long id2;

	public BinaryId(long id1, long id2) {
		this.id1 = id1;
		this.id2 = id2;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(id1 ^ Long.reverse(id2));
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof BinaryId && id1 == ((BinaryId) obj).id1 && id2 == ((BinaryId) obj).id2;
	}
}
