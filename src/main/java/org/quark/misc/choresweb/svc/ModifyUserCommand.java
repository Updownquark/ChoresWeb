package org.quark.misc.choresweb.svc;

public record ModifyUserCommand(long id, String email, Boolean god, Boolean globalAdmin) {
}
