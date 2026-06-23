package eu.wiegandt.librehousehold.usersettings.service;
import eu.wiegandt.librehousehold.usersettings.exception.*;
import eu.wiegandt.librehousehold.usersettings.mapper.*;
import eu.wiegandt.librehousehold.usersettings.model.*;
import eu.wiegandt.librehousehold.usersettings.repository.*;

import eu.wiegandt.librehousehold.household.MemberDeletion;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.model.UserPreferences;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserPreferencesMapperImpl.class, UsersettingsService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration"
})
class UsersettingsServiceIT {

    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    @Autowired
    private UsersettingsService service;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @MockitoBean
    private MemberQuery memberQuery;

    @MockitoBean
    private MemberDeletion memberDeletion;

    @Nested
    class updatePreferences {

        @Test
        void firstCall_insertsNewRow() {
            // given
            var memberId = UUID.randomUUID();
            var request = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);
            doReturn(true).when(memberQuery).memberExistsById(memberId);
            var expected = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            var result = service.updatePreferences(memberId, request);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
            assertThat(preferencesRepository.findById(memberId)).isPresent();
        }

        @Test
        void secondCall_overwritesExistingValues() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(memberQuery).memberExistsById(memberId);
            service.updatePreferences(memberId, new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.LIGHT)
                    .language(UserPreferences.LanguageEnum.EN));
            var expected = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            var result = service.updatePreferences(memberId, new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE));

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class deleteAccount {

        @Test
        void validMember_callsRemoveMember() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(memberQuery).memberExistsById(memberId);
            doReturn(false).when(memberQuery).isAdmin(memberId);

            // when
            service.deleteAccount(memberId);

            // then
            verify(memberDeletion).removeMember(memberId);
        }

        @Test
        void memberIsAdmin_throwsAdminCannotDeleteAccountException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(true).when(memberQuery).memberExistsById(memberId);
            doReturn(true).when(memberQuery).isAdmin(memberId);

            // when / then
            assertThatThrownBy(() -> service.deleteAccount(memberId))
                    .isInstanceOf(AdminCannotDeleteAccountException.class);
        }
    }

    @Nested
    class onMemberRemoved {

        @Test
        void existingData_deletedOnEvent() {
            // given
            var memberId = UUID.randomUUID();
            var entity = new UserPreferencesEntity(memberId, "light", "en");
            preferencesRepository.save(entity);
            assertThat(preferencesRepository.findById(memberId)).isPresent();

            // when — repository direkt aufrufen: testet den SQL-DELETE unabhängig vom
            // REQUIRES_NEW-TX-Kontext des @ApplicationModuleListener (unit-seitig abgedeckt)
            preferencesRepository.deleteByMemberId(memberId);

            // then
            assertThat(preferencesRepository.findById(memberId)).isEmpty();
        }
    }
}
