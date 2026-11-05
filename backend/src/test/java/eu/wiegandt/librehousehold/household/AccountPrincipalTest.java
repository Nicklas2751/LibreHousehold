package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPrincipalTest {

    private static final UUID MEMBER_ID = UUID.fromString("c9a4b5d6-e7f8-9012-cdef-ab3456789012");
    private static final String PASSWORD_HASH = "$argon2id$v=19$m=19456,t=2,p=1$secretSaltAndHash";

    @Nested
    class equalsAndHashCode {

        static Stream<Arguments> principals() {
            return Stream.of(
                    Arguments.of(AccountPrincipal.identifiedBy(MEMBER_ID), true),
                    Arguments.of(AccountPrincipal.identifiedBy(UUID.randomUUID()), false));
        }

        @ParameterizedTest
        @MethodSource("principals")
        void otherPrincipal_equalOnlyWithSameMemberId(AccountPrincipal other, boolean expectedEqual) {
            // given
            var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, false);

            // when
            var result = principal.equals(other) && principal.hashCode() == other.hashCode();

            // then
            assertThat(result).isEqualTo(expectedEqual);
        }
    }

    @Test
    void getUsername_anyPrincipal_memberIdString() {
        // given
        var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, true);

        // when
        var result = principal.getUsername();

        // then
        assertThat(result).isEqualTo(MEMBER_ID.toString());
    }

    @Test
    void eraseCredentials_withHash_passwordNull() {
        // given
        var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, true);

        // when
        principal.eraseCredentials();

        // then
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void toString_anyPrincipal_containsNoHash() {
        // given
        var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, true);

        // when
        var result = principal.toString();

        // then
        assertThat(result).doesNotContain(PASSWORD_HASH);
    }

    @Test
    void isEnabled_unverified_false() {
        // given
        var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, false);

        // when
        var result = principal.isEnabled();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isEnabled_verified_true() {
        // given
        var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, true);

        // when
        var result = principal.isEnabled();

        // then
        assertThat(result).isTrue();
    }

    @Test
    void identifiedBy_sameMemberId_equalsFullPrincipal() {
        // given
        var principal = new AccountPrincipal(MEMBER_ID, UUID.randomUUID(), PASSWORD_HASH, true);

        // when
        var result = AccountPrincipal.identifiedBy(MEMBER_ID);

        // then
        assertThat(result).isEqualTo(principal);
    }
}
