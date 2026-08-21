package eu.wiegandt.librehousehold.household.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountEntityTest {

    @Test
    void toString_accountWithPasswordHash_masksPasswordHash() {
        // given
        var passwordHash = "$argon2id$v=19$m=19456,t=2,p=1$secretSaltAndHash";
        var account = new AccountEntity(UUID.randomUUID(), passwordHash);

        // when
        var result = account.toString();

        // then
        assertThat(result).doesNotContain(passwordHash);
    }
}
