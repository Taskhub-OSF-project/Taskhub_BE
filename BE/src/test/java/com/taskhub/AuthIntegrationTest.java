package com.taskhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.request.UpdateProfileRequest;
import com.taskhub.enums.Role;
import com.taskhub.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshTokenRepository refreshTokenRepository;


    @Test
    void registerLoginRefreshLogoutFlow() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email("authflow@test.com")
                .password("password123")
                .fullName("Auth Flow User")
                .role(Role.STUDENT)
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String registerCookieHeader = registerResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = extractRefreshToken(registerCookieHeader);
        assertNotNull(refreshToken);
        assertTrue(registerCookieHeader.contains("HttpOnly"));
        assertTrue(registerCookieHeader.contains("Secure"));
        assertTrue(registerCookieHeader.contains("SameSite=None"));
        assertTrue(registerCookieHeader.contains("Path=/api/auth"));

        LoginRequest login = LoginRequest.builder()
                .email("authflow@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("taskhub_refresh", refreshToken)))
                .andExpect(status().isUnauthorized());

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("taskhub_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String accessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
        String newRefreshToken = extractRefreshToken(refreshResult.getResponse().getHeader("Set-Cookie"));
        assertNotEquals(refreshToken, newRefreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("taskhub_refresh", newRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("taskhub_refresh", newRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hirerCannotApplyToTask_rbac() throws Exception {
        String studentToken = registerAndLogin("student-rbac@test.com", Role.STUDENT);
        String hirerToken = registerAndLogin("hirer-rbac@test.com", Role.HIRER);

        mockMvc.perform(post("/api/applications/task/1")
                        .header("Authorization", "Bearer " + hirerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("student-rbac@test.com"));
    }

    @Test
    void updateProfile_success() throws Exception {
        String token = registerAndLogin("profile@test.com", Role.STUDENT);

        UpdateProfileRequest update = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .university("Test University")
                .build();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.university").value("Test University"));
    }

    private String registerAndLogin(String email, Role role) throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .email(email)
                .password("password123")
                .fullName("Test User")
                .role(role)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        LoginRequest login = LoginRequest.builder().email(email).password("password123").build();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private String extractRefreshToken(String setCookieHeader) {
        assertNotNull(setCookieHeader, "Refresh cookie must be issued");
        String prefix = "taskhub_refresh=";
        assertTrue(setCookieHeader.startsWith(prefix), "Unexpected refresh cookie name");
        int separator = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(prefix.length(), separator < 0 ? setCookieHeader.length() : separator);
    }
}
