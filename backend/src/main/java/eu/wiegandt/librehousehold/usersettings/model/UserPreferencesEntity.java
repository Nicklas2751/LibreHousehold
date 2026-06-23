package eu.wiegandt.librehousehold.usersettings.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "usersettings", value = "user_preferences")
public class UserPreferencesEntity implements Persistable<UUID> {

    @Id
    @Column("member_id")
    private final UUID memberId;
    private String theme;
    private String language;
    @Transient
    private boolean isNew = true;

    public UserPreferencesEntity(UUID memberId) {
        this.memberId = memberId;
    }

    @PersistenceCreator
    public UserPreferencesEntity(UUID memberId, String theme, String language) {
        this.memberId = memberId;
        this.theme = theme;
        this.language = language;
    }

    public void markExisting() {
        this.isNew = false;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public UUID getId() {
        return memberId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getTheme() {
        return theme;
    }

    public String getLanguage() {
        return language;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
