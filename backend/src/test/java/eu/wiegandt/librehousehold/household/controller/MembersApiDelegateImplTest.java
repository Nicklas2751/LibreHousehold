package eu.wiegandt.librehousehold.household.controller;
import eu.wiegandt.librehousehold.household.exception.*;
import eu.wiegandt.librehousehold.household.mapper.*;
import eu.wiegandt.librehousehold.household.model.*;
import eu.wiegandt.librehousehold.household.repository.*;
import eu.wiegandt.librehousehold.household.service.*;

import eu.wiegandt.librehousehold.model.InviteInfo;
import eu.wiegandt.librehousehold.model.Member;
import eu.wiegandt.librehousehold.model.MemberRegistration;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MembersApiDelegateImplTest {

    @Mock
    private MemberManagementService memberManagementService;

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
        void validRequest_delegatesToServiceAndReturns201() {
            // given
            var token = UUID.randomUUID();
            var registration = Instancio.create(MemberRegistration.class);
            var member = Instancio.create(Member.class);
            doReturn(member).when(memberManagementService).joinHousehold(token, registration);

            // when
            var result = delegate.joinHousehold(token, registration);

            // then
            verify(memberManagementService).joinHousehold(token, registration);
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
