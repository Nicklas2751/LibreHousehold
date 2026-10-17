package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.household.exception.MemberAlreadyExistsException;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.household.service.AccountTokenService;
import eu.wiegandt.librehousehold.household.service.MemberManagementService;

import eu.wiegandt.librehousehold.api.MembersApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MembersApiController.class)
@Import(MembersApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class MembersValidationIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberManagementService memberManagementService;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private AccountTokenService accountTokenService;

    @Test
    void updateMember_emailTakenByAnotherAccount_returnsAccountAlreadyExistsProblemType() throws Exception {
        // given
        var householdId = UUID.randomUUID();
        var memberId = UUID.randomUUID();
        doThrow(new MemberAlreadyExistsException()).when(memberManagementService)
                .updateMember(eq(householdId), eq(memberId), any());

        // when / then
        mockMvc.perform(patch("/v1/household/{householdId}/members/{memberId}", householdId, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"taken@example.com\"}"))
                .andExpect(jsonPath("$.type").value("/problems/account-already-exists"));
    }

    @Test
    void checkEmailAvailability_malformedEmail_returns400() throws Exception {
        // when / then
        mockMvc.perform(get("/v1/members/availability").param("email", "not-an-email"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkEmailAvailability_malformedEmail_responseBodyContainsNoStacktrace() throws Exception {
        // when / then
        mockMvc.perform(get("/v1/members/availability").param("email", "not-an-email"))
                .andExpect(content().string(not(containsString("at org.springframework"))));
    }

    @Test
    void checkEmailAvailability_validEmail_returns200() throws Exception {
        // given
        doReturn(true).when(memberManagementService).isEmailAvailable("max@example.com");

        // when / then
        mockMvc.perform(get("/v1/members/availability").param("email", "max@example.com"))
                .andExpect(status().isOk());
    }
}
