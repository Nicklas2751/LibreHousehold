package eu.wiegandt.librehousehold.tasks.controller;

import eu.wiegandt.librehousehold.api.TasksApiController;
import eu.wiegandt.librehousehold.core.CurrentUserIdProvider;
import eu.wiegandt.librehousehold.auth.MethodSecurityTestConfig;
import eu.wiegandt.librehousehold.tasks.service.TaskService;
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

import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TasksApiController.class)
@Import({TasksApiDelegateImpl.class, MethodSecurityTestConfig.class})
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class TasksSecurityIT {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskService taskService;

    @MockitoBean
    CurrentUserIdProvider currentUserIdProvider;

    @Nested
    class getTasks {

        @Test
        void withoutToken_returns401() throws Exception {
            // given / when / then
            mockMvc.perform(get("/v1/household/{householdId}/tasks", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void withWrongHousehold_returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/tasks", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", otherHouseholdId.toString()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withCorrectHousehold_returns200() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            doReturn(List.of()).when(taskService).getTasks(householdId);

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/tasks", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", householdId.toString()))))
                    .andExpect(status().isOk());
        }
    }
}
