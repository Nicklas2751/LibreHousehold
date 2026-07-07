package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.MembersApiController;
import eu.wiegandt.librehousehold.auth.MethodSecurityTestConfig;
import eu.wiegandt.librehousehold.core.CurrentUserIdProvider;
import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import eu.wiegandt.librehousehold.core.SessionEstablishment;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MembersApiController.class)
@Import({MembersApiDelegateImpl.class, MethodSecurityTestConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class MembersSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberManagementService memberManagementService;

    @MockitoBean
    ResourceOwnerQuery resourceOwnerQuery;

    @MockitoBean
    SessionEstablishment sessionEstablishment;

    @MockitoBean
    CurrentUserIdProvider currentUserIdProvider;

    @Nested
    class getMembers {

        @Test
        void withoutToken_returns401() throws Exception {
            // given / when / then
            mockMvc.perform(get("/v1/household/{householdId}/members", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void withWrongHousehold_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/members", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", otherHouseholdId.toString()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withCorrectHousehold_returns200() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(memberManagementService).getMembers(householdId);

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/members", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", householdId.toString()))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class removeMember {

        @Test
        void withMemberRole_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}/members/{memberId}", householdId, memberId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", householdId.toString())
                                    .claim("role", "member"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withAdminRole_returns204() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();

            // when / then
            mockMvc.perform(delete("/v1/household/{householdId}/members/{memberId}", householdId, memberId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", householdId.toString())
                                    .claim("role", "admin"))))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class updateMember {

        @Test
        void withDifferentAccountId_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var otherAccountId = UUID.randomUUID();
            doReturn(false).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(patch("/v1/household/{householdId}/members/{memberId}", householdId, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(jwt().jwt(b -> b
                                    .subject(otherAccountId.toString())
                                    .claim("household_id", householdId.toString()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withMatchingAccountId_returns204() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(true).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(patch("/v1/household/{householdId}/members/{memberId}", householdId, memberId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(jwt().jwt(b -> b
                                    .subject(memberId.toString())
                                    .claim("household_id", householdId.toString()))))
                    .andExpect(status().isNoContent());
        }
    }
}
