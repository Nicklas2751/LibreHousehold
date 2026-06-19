package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.api.TasksApiController;
import eu.wiegandt.librehousehold.model.Task;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TasksApiController.class)
@Import(TasksApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class TasksValidationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @Nested
    class createTask {

        @Test
        void titleTooShort_returns400() throws Exception {
            // given
            var task = new Task(UUID.randomUUID(), "ab", LocalDate.of(2026, 1, 15));

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/tasks", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(task)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void titleTooLong_returns400() throws Exception {
            // given
            var task = new Task(UUID.randomUUID(), "x".repeat(201), LocalDate.of(2026, 1, 15));

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/tasks", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(task)))
                    .andExpect(status().isBadRequest());
        }
    }
}