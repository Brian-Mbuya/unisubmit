package com.unisubmit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-level access-control matrix (V2.5a). Every assertion resolves at the security
 * layer or on a public endpoint, so a mock principal is enough — none of these execute a
 * controller that needs the real CustomUserDetails.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityMatrixTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentIsForbiddenFromLecturerArea() throws Exception {
        mvc.perform(get("/lecturer/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentIsForbiddenFromAdminArea() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTURER")
    void lecturerIsForbiddenFromAdminArea() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLoginFromStudentArea() throws Exception {
        mvc.perform(get("/student/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonymousIsRedirectedToLoginFromAdminArea() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void aboutIsPublic() throws Exception {
        mvc.perform(get("/about")).andExpect(status().isOk());
    }
}
