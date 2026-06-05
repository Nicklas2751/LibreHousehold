package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.api.TasksApiController;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskUpdate;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TasksApiController.class)
@Import(TasksApiDelegateImpl.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class TasksApiDelegateImplIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @Nested
    class getTasks {

        @Test
        void validHouseholdId_returns200WithTaskList() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var task = new Task(UUID.randomUUID(), "Clean kitchen", LocalDate.of(2024, 7, 1));
            doReturn(List.of(task)).when(taskService).getTasks(householdId);

            // when / then
            mockMvc.perform(get("/v1/household/{householdId}/tasks", householdId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].title").value("Clean kitchen"));
        }
    }

    @Nested
    class createTask {

        @Test
        void validBody_returns200WithCreatedTask() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var task = new Task(UUID.randomUUID(), "Take out trash", LocalDate.of(2024, 7, 1));
            doReturn(task).when(taskService).createTask(eq(householdId), any(Task.class));

            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/tasks", householdId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(task)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Take out trash"));
        }

        @Test
        void missingBody_returns400() throws Exception {
            // when / then
            mockMvc.perform(post("/v1/household/{householdId}/tasks", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class updateTask {

        @Test
        void validBody_returns200WithUpdatedTask() throws Exception {
            // given
            var taskId = UUID.randomUUID();
            var doneDate = LocalDate.of(2024, 7, 5);
            var updated = new Task(taskId, "Clean", LocalDate.of(2024, 7, 1)).done(doneDate);
            doReturn(updated).when(taskService).updateTask(eq(taskId), any(TaskUpdate.class));

            // when / then
            mockMvc.perform(patch("/v1/household/{householdId}/tasks/{taskId}", UUID.randomUUID(), taskId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new TaskUpdate().done(doneDate))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(taskId.toString()));
        }

        @Test
        void missingBody_returns400() throws Exception {
            // when / then
            mockMvc.perform(patch("/v1/household/{householdId}/tasks/{taskId}", UUID.randomUUID(), UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }
}