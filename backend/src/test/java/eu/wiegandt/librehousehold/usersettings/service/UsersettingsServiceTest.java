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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersettingsServiceTest {

    @Mock
    private UserPreferencesRepository preferencesRepository;

    @Spy
    private UserPreferencesMapper preferencesMapper = Mappers.getMapper(UserPreferencesMapper.class);

    @Mock
    private MemberQuery memberQuery;

    @Mock
    private MemberDeletion memberDeletion;

    @InjectMocks
    private UsersettingsService service;

    @Nested
    class updatePreferences {

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(false).when(memberQuery).memberExistsById(memberId);

            // when / then
            assertThatThrownBy(() -> service.updatePreferences(memberId, new UserPreferences()))
                    .isInstanceOf(MemberNotFoundException.class);
        }

        @Test
        void noExistingPreferences_insertsNewEntityAndReturnsPreferences() {
            // given
            var memberId = UUID.randomUUID();
            var request = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);
            doReturn(true).when(memberQuery).memberExistsById(memberId);
            doReturn(Optional.empty()).when(preferencesRepository).findById(memberId);
            var expected = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            var result = service.updatePreferences(memberId, request);

            // then
            verify(preferencesRepository).save(any(UserPreferencesEntity.class));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void existingPreferences_updatesEntityAndReturnsPreferences() {
            // given
            var memberId = UUID.randomUUID();
            var request = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);
            doReturn(true).when(memberQuery).memberExistsById(memberId);
            doReturn(Optional.of(new UserPreferencesEntity(memberId, "light", "en")))
                    .when(preferencesRepository).findById(memberId);
            var expected = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            var result = service.updatePreferences(memberId, request);

            // then
            verify(preferencesRepository).save(any(UserPreferencesEntity.class));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class deleteAccount {

        @Test
        void memberNotFound_throwsMemberNotFoundException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(false).when(memberQuery).memberExistsById(memberId);

            // when / then
            assertThatThrownBy(() -> service.deleteAccount(memberId))
                    .isInstanceOf(MemberNotFoundException.class);
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
    }

    @Nested
    class onMemberRemoved {

        @Test
        void publishedEvent_deletesPreferences() {
            // given
            var memberId = UUID.randomUUID();
            var event = new MemberRemoved(memberId);

            // when
            service.onMemberRemoved(event);

            // then
            verify(preferencesRepository).deleteByMemberId(memberId);
        }
    }
}
