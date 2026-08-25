package org.quark.misc.choresweb.svc;

import java.util.Set;

public record ModifyWorkerCommand(long organization, long user, Boolean manager, Boolean worker, Integer level, Set<String> labels) {
}
