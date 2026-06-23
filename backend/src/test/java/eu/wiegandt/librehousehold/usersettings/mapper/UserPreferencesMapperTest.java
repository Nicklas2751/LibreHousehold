package eu.wiegandt.librehousehold.usersettings.mapper;

import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.model.UserPreferencesEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferencesMapperTest {

    private final UserPreferencesMapper mapper = Mappers.getMapper(UserPreferencesMapper.class);

    @Nested
    class toUserPreferences {

        @Test
        void bothFieldsSet_returnsMappedPreferences() {
            // given
            var entity = new UserPreferencesEntity(UUID.randomUUID(), "dark", "de");
            var expected = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            var result = mapper.toUserPreferences(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nullTheme_returnsEmptyOptionalForTheme() {
            // given
            var entity = new UserPreferencesEntity(UUID.randomUUID(), null, "en");
            var expected = new UserPreferences()
                    .language(UserPreferences.LanguageEnum.EN);

            // when
            var result = mapper.toUserPreferences(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void nullLanguage_returnsEmptyOptionalForLanguage() {
            // given
            var entity = new UserPreferencesEntity(UUID.randomUUID(), "light", null);
            var expected = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.LIGHT);

            // when
            var result = mapper.toUserPreferences(entity);

            // then
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class updateEntityFromPreferences {

        @Test
        void bothFieldsSet_updatesEntityFields() {
            // given
            var entity = new UserPreferencesEntity(UUID.randomUUID(), "light", "en");
            var source = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            mapper.updateEntityFromPreferences(source, entity);

            // then
            assertThat(entity.getTheme()).isEqualTo("dark");
            assertThat(entity.getLanguage()).isEqualTo("de");
        }

        @Test
        void emptyTheme_setsThemeToNull() {
            // given
            var entity = new UserPreferencesEntity(UUID.randomUUID(), "light", "en");
            var source = new UserPreferences()
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            mapper.updateEntityFromPreferences(source, entity);

            // then
            assertThat(entity.getTheme()).isNull();
            assertThat(entity.getLanguage()).isEqualTo("de");
        }

        @Test
        void memberIdNotOverwritten() {
            // given
            var memberId = UUID.randomUUID();
            var entity = new UserPreferencesEntity(memberId, "light", "en");
            var source = new UserPreferences()
                    .theme(UserPreferences.ThemeEnum.DARK)
                    .language(UserPreferences.LanguageEnum.DE);

            // when
            mapper.updateEntityFromPreferences(source, entity);

            // then
            assertThat(entity.getMemberId()).isEqualTo(memberId);
        }
    }
}
