package eu.wiegandt.librehousehold.household;

import java.util.UUID;

public record MemberEmailChanged(UUID memberId, String newEmail) {
}
