package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPrincipalTest {

    @Test
    void toString_principalWithPasswordHash_masksPasswordHash() {
        // given
        var passwordHash = "$argon2id$v=19$m=19456,t=2,p=1$secretSaltAndHash";
        var principal = new AccountPrincipal("max@example.com", passwordHash, true);

        // when
        var result = principal.toString();

        // then
        assertThat(result).doesNotContain(passwordHash);
    }

    @Nested
    class isEnabled {

        @Test
        void unverifiedEmail_returnsFalse() {
            // given
            var principal = new AccountPrincipal("max@example.com", "$argon2id$...", false);

            // when
            var result = principal.isEnabled();

            // then
            assertThat(result).isFalse();
        }

        @Test
        void verifiedEmail_returnsTrue() {
            // given
            var principal = new AccountPrincipal("max@example.com", "$argon2id$...", true);

            // when
            var result = principal.isEnabled();

            // then
            assertThat(result).isTrue();
        }
    }
}
