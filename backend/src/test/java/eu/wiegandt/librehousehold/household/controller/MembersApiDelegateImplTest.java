package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.core.CurrentUserIdProvider;
import eu.wiegandt.librehousehold.core.SessionEstablishment;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;
import eu.wiegandt.librehousehold.model.*;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MembersApiDelegateImplTest {

    @Mock
    private MemberManagementService memberManagementService;

    @Mock
    private SessionEstablishment sessionEstablishment;

    @Mock
    private CurrentUserIdProvider currentUserIdProvider;

    @InjectMocks
    private MembersApiDelegateImpl delegate;

    @Nested
    class getMembers {

        @Test
        void validRequest_delegatesToServiceAndReturns200() {
            // given
            var householdId = UUID.randomUUID();
            var members = Instancio.ofList(Member.class).size(2).create();
            doReturn(members).when(memberManagementService).getMembers(householdId);

            // when
            var result = delegate.getMembers(householdId);

            // then
            verify(memberManagementService).getMembers(householdId);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(members);
        }
    }

    @Nested
    class getMember {

        @Test
        void validRequest_delegatesToServiceWithMemberIdAndReturns200() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var member = Instancio.create(Member.class);
            doReturn(member).when(memberManagementService).getMember(memberId);

            // when
            var result = delegate.getMember(householdId, memberId);

            // then
            verify(memberManagementService).getMember(memberId);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(member);
        }
    }

    @Nested
    class resolveInvite {

        @Test
        void validRequest_delegatesToServiceAndReturns200() {
            // given
            var token = UUID.randomUUID();
            var inviteInfo = Instancio.create(InviteInfo.class);
            doReturn(inviteInfo).when(memberManagementService).resolveInvite(token);

            // when
            var result = delegate.resolveInvite(token);

            // then
            verify(memberManagementService).resolveInvite(token);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(inviteInfo);
        }
    }

    @Nested
    class joinHousehold {

        @Test
        void validRegistration_delegatesToServiceThenEstablishesSession() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(LocalMemberRegistration.class);
            var member = Instancio.create(Member.class);
            doReturn(member).when(memberManagementService).joinHouseholdLocal(token, registration);

            // when
            delegate.joinHousehold(token, registration);

            // then
            var order = inOrder(memberManagementService, sessionEstablishment);
            order.verify(memberManagementService).joinHouseholdLocal(token, registration);
            order.verify(sessionEstablishment).establishSession(registration.getEmail());
        }

        @Test
        void validRegistration_returns201WithMemberBody() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(LocalMemberRegistration.class);
            var member = Instancio.create(Member.class);
            doReturn(member).when(memberManagementService).joinHouseholdLocal(token, registration);

            // when
            var result = delegate.joinHousehold(token, registration);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isEqualTo(member);
        }
    }

    @Nested
    class joinHouseholdAuthenticated {

        @Test
        void authenticatedUser_delegatesWithCurrentUserIdAndDoesNotTouchLocalRegistrationPorts() {
            // given
            var accountId = UUID.randomUUID();
            var householdJoin = Instancio.create(HouseholdJoin.class);
            var member = Instancio.create(Member.class);
            doReturn(accountId).when(currentUserIdProvider).getCurrentUserId();
            doReturn(member).when(memberManagementService).joinHouseholdAuthenticated(
                    accountId, householdJoin.getToken(), householdJoin.getMemberName(),
                    householdJoin.getMemberAvatar().orElse(null));

            // when
            delegate.joinHouseholdAuthenticated(householdJoin);

            // then
            verify(memberManagementService).joinHouseholdAuthenticated(
                    accountId, householdJoin.getToken(), householdJoin.getMemberName(),
                    householdJoin.getMemberAvatar().orElse(null));
            verifyNoInteractions(sessionEstablishment);
        }

        @Test
        void validRequest_returns201WithMemberBody() {
            // given
            var accountId = UUID.randomUUID();
            var householdJoin = Instancio.create(HouseholdJoin.class);
            var member = Instancio.create(Member.class);
            doReturn(accountId).when(currentUserIdProvider).getCurrentUserId();
            doReturn(member).when(memberManagementService).joinHouseholdAuthenticated(
                    accountId, householdJoin.getToken(), householdJoin.getMemberName(),
                    householdJoin.getMemberAvatar().orElse(null));

            // when
            var result = delegate.joinHouseholdAuthenticated(householdJoin);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isEqualTo(member);
        }
    }

    @Nested
    class updateMember {

        @Test
        void validRequest_delegatesToServiceAndReturns204() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var update = Instancio.create(MemberUpdate.class);

            // when
            var result = delegate.updateMember(householdId, memberId, update);

            // then
            verify(memberManagementService).updateMember(memberId, update);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    class removeMember {

        @Test
        void validRequest_delegatesToServiceAndReturns204() {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();

            // when
            var result = delegate.removeMember(householdId, memberId);

            // then
            verify(memberManagementService).removeMember(memberId);
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
