package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPrincipalTest {

    @Test
    void toString_principalWithPasswordHash_masksPasswordHash() {
        // given
        var passwordHash = "$argon2id$v=19$m=19456,t=2,p=1$secretSaltAndHash";
        var principal = new AccountPrincipal("max@example.com", passwordHash);

        // when
        var result = principal.toString();

        // then
        assertThat(result).doesNotContain(passwordHash);
    }
}
