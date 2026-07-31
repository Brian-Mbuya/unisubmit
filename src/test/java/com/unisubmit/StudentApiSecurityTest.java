package com.unisubmit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unisubmit.domain.Role;
import com.unisubmit.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The /api/v1 chain is stateless and separate from the browser's session chain, so none
 * of the existing SecurityMatrixTest coverage applies to it. These pin the properties the
 * Flutter client depends on: no token means no data, a student's token cannot reach the
 * class-rep endpoints, and the CLASS_REP authority is genuinely absent from an ordinary
 * student's token rather than merely unchecked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentApiSecurityTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;

    // Constructed directly: this app builds its ObjectMappers by hand (see
    // AdminBrandingController, BrandingService) rather than relying on Boot's JSON
    // auto-configuration, so there is no ObjectMapper bean to inject.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userService.createStudent("SCT-API-1", "0712000001", "apiPass1", "Api Student", null, 1, 1);
    }

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
        return (String) parsed.get("token");
    }

    @Test
    void studentCanObtainATokenWithTheirAdmissionNumber() throws Exception {
        // Students have no email — the admission number IS the username on this chain too.
        String token = tokenFor("SCT-API-1", "apiPass1");
        assertNotNull(token);
    }

    @Test
    void submissionsRequireABearerToken() throws Exception {
        mvc.perform(get("/api/v1/student/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aGarbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/submissions")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedStudentSeesOnlyTheirOwnSubmissionList() throws Exception {
        String token = tokenFor("SCT-API-1", "apiPass1");

        String body = mvc.perform(get("/api/v1/student/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // A brand-new student owns nothing; anything else means the list is not scoped
        // to the caller.
        assertEquals("[]", body.trim());
    }

    @Test
    void studentCannotReadASubmissionTheyDoNotOwn() throws Exception {
        String token = tokenFor("SCT-API-1", "apiPass1");

        // Ownership is enforced in SubmissionService, so an id they don't own must not
        // come back as 200 regardless of whether it exists.
        mvc.perform(get("/api/v1/student/submissions/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 200) {
                        throw new AssertionError(
                                "Student read a submission they do not own (HTTP 200).");
                    }
                });
    }

    @Test
    void ordinaryStudentCannotReachClassRepEndpoints() throws Exception {
        String token = tokenFor("SCT-API-1", "apiPass1");

        // The seeded student is not a rep, so hasAuthority('CLASS_REP') must reject this.
        mvc.perform(get("/api/v1/student/class-rep/classmates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void classRepEndpointsAlsoRejectAnonymousCallers() throws Exception {
        mvc.perform(get("/api/v1/student/class-rep/classmates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lecturerTokenCannotReachStudentSubmissionEndpoints() throws Exception {
        userService.createUser("api.lecturer@university.edu", "apiPass1", "Api Lecturer",
                Role.LECTURER, null, "L-API-1");
        String token = tokenFor("api.lecturer@university.edu", "apiPass1");

        mvc.perform(get("/api/v1/student/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
