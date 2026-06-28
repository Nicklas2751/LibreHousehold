package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthorizationLayerIT.TestEndpoints.class})
class AuthorizationLayerIT {

    @TestConfiguration
    static class TestEndpoints {

        @Bean
        TestAuthController testAuthController() {
            return new TestAuthController();
        }
    }

    @RestController
    @RequestMapping("/v1-authtest/household/{householdId}")
    static class TestAuthController {

        @GetMapping("/members-only")
        @InHousehold
        ResponseEntity<Void> membersOnly(@PathVariable UUID householdId) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/admin-only")
        @OnlyAdmin
        ResponseEntity<Void> adminOnly(@PathVariable UUID householdId) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/resource/{resourceId}")
        @OnlyAuthor
        ResponseEntity<Void> authorOnly(@PathVariable UUID householdId, @PathVariable UUID resourceId) {
            return ResponseEntity.ok().build();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ResourceOwnerQuery resourceOwnerQuery;

    @Nested
    class withoutToken {

        @Test
        void returns401() throws Exception {
            // given / when / then
            mockMvc.perform(get("/v1-authtest/household/{id}/members-only", UUID.randomUUID())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class withTokenFromDifferentHousehold {

        @Test
        void returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var otherHouseholdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1-authtest/household/{id}/members-only", householdId)
                            .with(jwt().jwt(b -> b.claim("household_id", otherHouseholdId.toString())))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class memberTokenOnAdminEndpoint {

        @Test
        void returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1-authtest/household/{id}/admin-only", householdId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", householdId.toString())
                                    .claim("role", "member")))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class adminTokenOnAdminEndpoint {

        @Test
        void returns200() throws Exception {
            // given
            var householdId = UUID.randomUUID();

            // when / then
            mockMvc.perform(get("/v1-authtest/household/{id}/admin-only", householdId)
                            .with(jwt().jwt(b -> b
                                    .claim("household_id", householdId.toString())
                                    .claim("role", "admin")))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class wrongAuthorOnAuthorEndpoint {

        @Test
        void returns403() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();
            var accountId = UUID.randomUUID();
            doReturn(false).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(get("/v1-authtest/household/{hid}/resource/{rid}", householdId, resourceId)
                            .with(jwt().jwt(b -> b
                                    .subject(accountId.toString())
                                    .claim("household_id", householdId.toString())))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class correctAuthorOnAuthorEndpoint {

        @Test
        void returns200() throws Exception {
            // given
            var householdId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();
            var accountId = UUID.randomUUID();
            doReturn(true).when(resourceOwnerQuery).isOwner(any(), any());

            // when / then
            mockMvc.perform(get("/v1-authtest/household/{hid}/resource/{rid}", householdId, resourceId)
                            .with(jwt().jwt(b -> b
                                    .subject(accountId.toString())
                                    .claim("household_id", householdId.toString())))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }
}
