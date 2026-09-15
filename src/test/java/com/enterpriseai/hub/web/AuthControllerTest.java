package com.enterpriseai.hub.web;

import com.enterpriseai.hub.config.AppProperties;
import com.enterpriseai.hub.dto.auth.AuthResponse;
import com.enterpriseai.hub.dto.auth.LoginRequest;
import com.enterpriseai.hub.dto.auth.RegisterRequest;
import com.enterpriseai.hub.dto.user.UserResponse;
import com.enterpriseai.hub.exception.ConflictException;
import com.enterpriseai.hub.security.JwtService;
import com.enterpriseai.hub.security.RestAccessDeniedHandler;
import com.enterpriseai.hub.security.RestAuthenticationEntryPoint;
import com.enterpriseai.hub.security.SecurityConfig;
import com.enterpriseai.hub.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtService jwtService;

    private AuthResponse sampleAuthResponse() {
        UserResponse user = new UserResponse(1L, "jane@acme.test", "Jane Doe", "Acme", "Engineer",
                true, List.of("ROLE_USER"), Instant.now(), null);
        return AuthResponse.of("access-token", "refresh-token", 1800, user);
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 with a token pair")
    void registerReturnsCreated() throws Exception {
        when(authService.register(any(), any())).thenReturn(sampleAuthResponse());
        RegisterRequest request =
                new RegisterRequest("jane@acme.test", "Str0ngPassphrase", "Jane Doe", "Acme", "Engineer");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("jane@acme.test"));
    }

    @Test
    @DisplayName("a weak password is rejected with a field-level validation error")
    void rejectsWeakPassword() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@acme.test", "weak", "Jane Doe", null, null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("password"));
    }

    @Test
    @DisplayName("a malformed e-mail is rejected")
    void rejectsInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "Str0ngPassphrase", "Jane", null, null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a duplicate e-mail surfaces as 409 CONFLICT with a stable error code")
    void duplicateEmailReturnsConflict() throws Exception {
        when(authService.register(any(), any())).thenThrow(ConflictException.emailTaken("jane@acme.test"));
        RegisterRequest request = new RegisterRequest("jane@acme.test", "Str0ngPassphrase", "Jane", null, null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 for valid credentials")
    void loginSucceeds() throws Exception {
        when(authService.login(any(), any())).thenReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("jane@acme.test", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(1800));
    }

    @Test
    @DisplayName("bad credentials return 401 INVALID_CREDENTIALS, never a stack trace")
    void loginFailsWithUnauthorized() throws Exception {
        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("jane@acme.test", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid e-mail address or password"));
    }

    @Test
    @DisplayName("a protected endpoint is unreachable without a token")
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }
}
