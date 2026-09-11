package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.session.SessionRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * {@code webEnvironment = MOCK} (not {@code NONE}): the {@code SessionRegistry} bean and its
 * {@code SessionInformation} entries are only meaningfully exercised in a servlet-aware context,
 * analogous to {@code HouseholdSetupServiceIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
@ExtendWith(InstancioExtension.class)
class PasswordResetServiceIT {

    private static final String PASSWORD_RESET_PURPOSE = "PASSWORD_RESET";

    @Autowired
    private PasswordResetService service;

    @Autowired
    private AccountTokenService accountTokenService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SessionRegistry sessionRegistry;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Test
    void confirmPasswordReset_existingSession_expiresAllSessionsForThatAccount() {
        // given
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .create());
        var oldPasswordHash = "$argon2id$v=19$m=19456,t=2,p=1$oldHash";
        accountRepository.save(new AccountEntity(member.getId(), oldPasswordHash, true, Instant.now(), null));
        var principal = new AccountPrincipal(member.email(), oldPasswordHash, true);
        var sessionId = "session-" + UUID.randomUUID();
        sessionRegistry.registerNewSession(sessionId, principal);
        var token = accountTokenService.issueToken(member.getId(), PASSWORD_RESET_PURPOSE, Duration.ofHours(1));

        // when
        service.confirmPasswordReset(token, "new correct horse battery staple");

        // then
        assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isTrue();
    }
}
