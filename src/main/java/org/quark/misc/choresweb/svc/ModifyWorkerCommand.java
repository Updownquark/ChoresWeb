package org.quark.misc.choresweb.svc;

import java.util.Set;

public record ModifyWorkerCommand(long orgId, long userId, String name, Boolean manager, Boolean worker, Integer level,
	Set<String> labels) {
}
