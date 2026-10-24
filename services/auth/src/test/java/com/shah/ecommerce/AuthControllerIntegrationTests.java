package com.shah.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shah.ecommerce.auth.AuthController;
import com.shah.ecommerce.auth.AuthResponse;
import com.shah.ecommerce.auth.AuthService;
import com.shah.ecommerce.auth.JwtService;
import com.shah.ecommerce.config.SecurityConfig;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.user.AppUser;
import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({AuthService.class, JwtService.class, SecurityConfig.class, GLobalExceptionHandler.class})
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    private final Map<String, AppUser> store = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        when(userRepository.existsByUsername(anyString())).thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
        when(userRepository.existsByEmail(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            return store.values().stream().anyMatch(u -> email.equals(u.getEmail()));
        });
        when(userRepository.findByUsername(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(userRepository.findByEmail(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            return store.values().stream().filter(u -> email.equals(u.getEmail())).findFirst();
        });
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser user = inv.getArgument(0);
            store.put(user.getUsername(), user);
            return user;
        });
    }

    @Test
    void defaultAdminAndUserCanLoginAndReceiveSignedRoleTokens() throws Exception {
        String adminResponseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "admin123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("admin_role"))
                .andReturn().getResponse().getContentAsString();

        AuthResponse adminAuth = objectMapper.readValue(adminResponseJson, AuthResponse.class);
        Jwt decodedAdmin = jwtDecoder.decode(adminAuth.token());
        assertThat(decodedAdmin.getSubject()).isEqualTo("admin");
        assertThat(decodedAdmin.getClaimAsString("role")).isEqualTo("admin_role");

        String userResponseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "user",
                                  "password": "user123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("user_role"))
                .andReturn().getResponse().getContentAsString();

        AuthResponse userAuth = objectMapper.readValue(userResponseJson, AuthResponse.class);
        Jwt decodedUser = jwtDecoder.decode(userAuth.token());
        assertThat(decodedUser.getClaimAsString("role")).isEqualTo("user_role");
    }

    @Test
    void invalidCredentialsAndRegisterWorkWithHumanReadableResponses() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username/email or password. Please verify your credentials and try again."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "secret123",
                                  "role": "user_role"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value(Role.user_role.name()))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
