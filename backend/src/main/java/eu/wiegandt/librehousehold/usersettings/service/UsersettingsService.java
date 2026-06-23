package eu.wiegandt.librehousehold.usersettings.service;

import eu.wiegandt.librehousehold.household.MemberDeletion;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.MemberRemoved;
import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.exception.AdminCannotDeleteAccountException;
import eu.wiegandt.librehousehold.usersettings.exception.MemberNotFoundException;
import eu.wiegandt.librehousehold.usersettings.mapper.UserPreferencesMapper;
import eu.wiegandt.librehousehold.usersettings.model.UserPreferencesEntity;
import eu.wiegandt.librehousehold.usersettings.repository.UserPreferencesRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UsersettingsService {

    private final UserPreferencesRepository preferencesRepository;
    private final UserPreferencesMapper preferencesMapper;
    private final MemberQuery memberQuery;
    private final MemberDeletion memberDeletion;

    public UsersettingsService(UserPreferencesRepository preferencesRepository,
                               UserPreferencesMapper preferencesMapper,
                               MemberQuery memberQuery,
                               MemberDeletion memberDeletion) {
        this.preferencesRepository = preferencesRepository;
        this.preferencesMapper = preferencesMapper;
        this.memberQuery = memberQuery;
        this.memberDeletion = memberDeletion;
    }

    @Transactional
    public UserPreferences updatePreferences(UUID memberId, UserPreferences request) {
        if (!memberQuery.memberExistsById(memberId)) {
            throw new MemberNotFoundException();
        }
        var entity = preferencesRepository.findById(memberId)
                .map(existing -> { existing.markExisting(); return existing; })
                .orElseGet(() -> new UserPreferencesEntity(memberId));
        preferencesMapper.updateEntityFromPreferences(request, entity);
        preferencesRepository.save(entity);
        return preferencesMapper.toUserPreferences(entity);
    }

    @Transactional
    public void deleteAccount(UUID memberId) {
        if (!memberQuery.memberExistsById(memberId)) {
            throw new MemberNotFoundException();
        }
        if (memberQuery.isAdmin(memberId)) {
            throw new AdminCannotDeleteAccountException();
        }
        memberDeletion.removeMember(memberId);
    }

    @ApplicationModuleListener
    public void onMemberRemoved(MemberRemoved event) {
        preferencesRepository.deleteByMemberId(event.memberId());
    }
}
