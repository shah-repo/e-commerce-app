package com.shah.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shah.ecommerce.auth.AuthController;
import com.shah.ecommerce.auth.AuthResponse;
import com.shah.ecommerce.auth.AuthService;
import com.shah.ecommerce.auth.LoginRequest;
import com.shah.ecommerce.auth.RegisterRequest;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.security.JwtService;
import com.shah.ecommerce.security.SecurityConfig;
import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.User;
import com.shah.ecommerce.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, AuthService.class, GLobalExceptionHandler.class})
class AuthApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private final Map<String, User> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID().toString());
            }
            store.put(u.getUsername(), u);
            return u;
        });
        when(userRepository.findByUsername(anyString())).thenAnswer(inv -> {
            String username = inv.getArgument(0);
            return Optional.ofNullable(store.get(username));
        });
        when(userRepository.existsByUsername(anyString())).thenAnswer(inv -> {
            String username = inv.getArgument(0);
            return store.containsKey(username);
        });
    }

    @Test
    void shouldRegisterAndLoginAdminAndUserWithValidJwtTokens() throws Exception {
        RegisterRequest adminRegister = new RegisterRequest("admin", "admin@test.com", "admin123", Role.admin_role);
        String adminRegBody = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminRegister)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("admin_role"))
                .andReturn().getResponse().getContentAsString();

        AuthResponse adminAuth = objectMapper.readValue(adminRegBody, AuthResponse.class);
        Jwt decodedAdmin = jwtDecoder.decode(adminAuth.token());
        assertThat(decodedAdmin.getSubject()).isEqualTo("admin");
        assertThat(decodedAdmin.getClaimAsString("role")).isEqualTo("admin_role");
        assertThat(decodedAdmin.getClaimAsStringList("roles")).containsExactly("admin_role");

        RegisterRequest userRegister = new RegisterRequest("john", "john@test.com", "secret123", Role.user_role);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegister)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("user_role"));

        LoginRequest loginRequest = new LoginRequest("john", "secret123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("user_role"));
    }

    @Test
    void shouldReturnHumanReadableUnauthorizedOnInvalidPassword() throws Exception {
        RegisterRequest userRegister = new RegisterRequest("alice", "alice@test.com", "correctPass", Role.user_role);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegister)))
                .andExpect(status().isOk());

        LoginRequest wrongPassword = new LoginRequest("alice", "wrongPass");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void shouldReturnHumanReadableErrorOnDuplicateUsernameAndDatabaseDown() throws Exception {
        RegisterRequest req = new RegisterRequest("dupUser", "dup@test.com", "pass123", Role.user_role);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username 'dupUser' is already taken. Please choose a different username."));

        when(userRepository.existsByUsername(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Mongo refused"));

        RegisterRequest req2 = new RegisterRequest("newUser", "new@test.com", "pass123", Role.user_role);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
