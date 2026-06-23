package eu.wiegandt.librehousehold.usersettings.mapper;

import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.model.UserPreferencesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Optional;

@Mapper
public interface UserPreferencesMapper {

    @Mapping(target = "theme", qualifiedByName = "toThemeEnum")
    @Mapping(target = "language", qualifiedByName = "toLanguageEnum")
    UserPreferences toUserPreferences(UserPreferencesEntity entity);

    @Mapping(target = "memberId", ignore = true)
    @Mapping(target = "theme", qualifiedByName = "fromThemeEnum")
    @Mapping(target = "language", qualifiedByName = "fromLanguageEnum")
    void updateEntityFromPreferences(UserPreferences source, @MappingTarget UserPreferencesEntity target);

    @Named("toThemeEnum")
    default Optional<UserPreferences.ThemeEnum> toThemeEnum(String value) {
        return Optional.ofNullable(value).map(UserPreferences.ThemeEnum::fromValue);
    }

    @Named("toLanguageEnum")
    default Optional<UserPreferences.LanguageEnum> toLanguageEnum(String value) {
        return Optional.ofNullable(value).map(UserPreferences.LanguageEnum::fromValue);
    }

    @Named("fromThemeEnum")
    default String fromThemeEnum(Optional<UserPreferences.ThemeEnum> value) {
        return value.map(UserPreferences.ThemeEnum::getValue).orElse(null);
    }

    @Named("fromLanguageEnum")
    default String fromLanguageEnum(Optional<UserPreferences.LanguageEnum> value) {
        return value.map(UserPreferences.LanguageEnum::getValue).orElse(null);
    }
}
