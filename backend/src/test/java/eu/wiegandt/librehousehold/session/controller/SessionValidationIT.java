package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.api.SessionApiController;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionApiController.class)
@Import(SessionApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class SessionValidationIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberQuery memberQuery;

    @MockitoBean
    private HouseholdQuery householdQuery;

    @MockitoBean
    private PreferencesQuery preferencesQuery;

    @Test
    void getCurrentUser_noAuthenticatedSession_returns401() throws Exception {
        // when / then
        mockMvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUser_noAuthenticatedSession_responseBodyIsProblemDetail() throws Exception {
        // when / then
        mockMvc.perform(get("/v1/me"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
