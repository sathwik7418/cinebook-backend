package com.cinebook.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "cinebook.storage.directory=target/security-test-data",
        "jwt.secret=test-only-secret-key-with-more-than-32-bytes",
        "jwt.expiration-ms=86400000"
})
@AutoConfigureMockMvc
class LegacyUserEndpointSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Test void legacyUserListAndByIdAreNotCustomerEndpoints() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/1")).andExpect(status().isUnauthorized());
    }

    @Test void newProfileEndpointAndBookingHistoryRequireAuthentication() throws Exception {
        mvc.perform(get("/api/profile/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/1/bookings")).andExpect(status().isUnauthorized());
    }

    @Test void feedEndpointsRejectMissingOrInvalidTokensAsUnauthorized() throws Exception {
        mvc.perform(get("/api/feed/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/feed/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test void registrationPersistsNameAndReturnsOnlyAnOwnerScopedProfile() throws Exception {
        String id=UUID.randomUUID().toString().substring(0, 8);
        MvcResult registration=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username","test"+id,"email",id+"@example.test",
                        "password","CineBook-Password-123!","name","Test Viewer"))))
                .andExpect(status().isOk()).andReturn();
        String token=mapper.readTree(registration.getResponse().getContentAsString()).path("token").asText();
        int accountId=mapper.readTree(registration.getResponse().getContentAsString()).path("id").asInt();
        mvc.perform(get("/api/profile/me").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.name").value("Test Viewer"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/"+accountId+"/bookings").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(get("/api/users/"+(accountId+100000)+"/bookings").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username","other"+id,"email",id+"@example.test",
                        "password","CineBook-Password-123!","name","Other Viewer"))))
                .andExpect(status().isConflict());
    }
}
