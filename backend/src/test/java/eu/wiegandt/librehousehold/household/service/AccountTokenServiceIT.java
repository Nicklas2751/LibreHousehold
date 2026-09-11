package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.exception.AccountTokenInvalidException;
import eu.wiegandt.librehousehold.household.model.AccountTokenEntity;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountTokenRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class AccountTokenServiceIT {

    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

    @Autowired
    private AccountTokenService accountTokenService;

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    private UUID createMember() {
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        return member.getId();
    }

    @Nested
    class issueToken {

        @Test
        void validCall_persistsTokenWithCorrectExpiry() {
            // given
            var memberId = createMember();
            var beforeIssuance = Instant.now();

            // when
            var token = accountTokenService.issueToken(memberId, EMAIL_VERIFICATION, Duration.ofHours(24));

            // then
            assertThat(accountTokenRepository.findByTokenAndPurpose(token, EMAIL_VERIFICATION))
                    .hasValueSatisfying(accountToken -> {
                        assertThat(accountToken.memberId()).isEqualTo(memberId);
                        assertThat(accountToken.validUntil()).isBetween(
                                beforeIssuance.plus(Duration.ofHours(24)), Instant.now().plus(Duration.ofHours(24)));
                    });
        }

        @Test
        void existingTokenForSamePurpose_deletesOldTokenFirst() {
            // given
            var memberId = createMember();
            var oldToken = accountTokenService.issueToken(memberId, EMAIL_VERIFICATION, Duration.ofHours(24));

            // when
            accountTokenService.issueToken(memberId, EMAIL_VERIFICATION, Duration.ofHours(24));

            // then
            assertThat(accountTokenRepository.findByTokenAndPurpose(oldToken, EMAIL_VERIFICATION)).isEmpty();
        }
    }

    @Nested
    class consumeToken {

        @Test
        void validToken_returnsMemberIdAndDeletesToken() {
            // given
            var memberId = createMember();
            var token = accountTokenService.issueToken(memberId, EMAIL_VERIFICATION, Duration.ofHours(24));

            // when
            var result = accountTokenService.consumeToken(token, EMAIL_VERIFICATION);

            // then
            assertThat(result).isEqualTo(memberId);
            assertThat(accountTokenRepository.findByTokenAndPurpose(token, EMAIL_VERIFICATION)).isEmpty();
        }

        @Test
        void expiredToken_throwsAccountTokenInvalidException() {
            // given
            var memberId = createMember();
            var expiredToken = UUID.randomUUID();
            accountTokenRepository.save(new AccountTokenEntity(
                    null, memberId, expiredToken, EMAIL_VERIFICATION, Instant.now().minus(Duration.ofHours(1))));

            // when / then
            assertThatThrownBy(() -> accountTokenService.consumeToken(expiredToken, EMAIL_VERIFICATION))
                    .isInstanceOf(AccountTokenInvalidException.class);
        }

        @Test
        void unknownToken_throwsAccountTokenInvalidException() {
            // when / then
            assertThatThrownBy(() -> accountTokenService.consumeToken(UUID.randomUUID(), EMAIL_VERIFICATION))
                    .isInstanceOf(AccountTokenInvalidException.class);
        }
    }
}
