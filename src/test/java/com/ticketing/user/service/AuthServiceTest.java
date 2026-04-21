package com.ticketing.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ticketing.common.security.JwtService;
import com.ticketing.user.dto.AuthResponse;
import com.ticketing.user.dto.LoginRequest;
import com.ticketing.user.dto.RegisterRequest;
import com.ticketing.user.model.Role;
import com.ticketing.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register with existing email should throw validation exception")
    void register_withExistingEmail_shouldThrowValidationException() {
        RegisterRequest request = createRegisterRequest();
        when(userRepository.save(any(com.ticketing.user.model.User.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Email registered"));

        assertThrows(ValidationException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("register with valid data should return auth response")
    void register_withValidData_shouldReturnAuthResponse() {
        RegisterRequest request = createRegisterRequest();
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");

        com.ticketing.user.model.User savedUser = com.ticketing.user.model.User.builder()
            .id(7L)
            .email(request.getEmail())
            .passwordHash("encoded-password")
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .role(Role.USER)
            .build();
        when(userRepository.save(any(com.ticketing.user.model.User.class))).thenReturn(savedUser);

        UserDetails userDetails = User.withUsername(savedUser.getEmail())
            .password("encoded-password")
            .authorities("ROLE_USER")
            .build();
        when(userDetailsService.loadUserByUsername(savedUser.getEmail())).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals(7L, response.getUserId());
        assertEquals(savedUser.getEmail(), response.getEmail());
        assertEquals(Role.USER, response.getRole());
    }

    @Test
    @DisplayName("register with ADMIN role in request should force USER role")
    void register_withAdminRole_shouldForceUserRole() {
        RegisterRequest request = createRegisterRequest();
        request.setRole(Role.ADMIN); // Attempt privilege escalation
        
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");

        com.ticketing.user.model.User savedUser = com.ticketing.user.model.User.builder()
            .id(8L)
            .email(request.getEmail())
            .passwordHash("encoded-password")
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .role(Role.USER) // Forced
            .build();
            
        when(userRepository.save(any(com.ticketing.user.model.User.class))).thenReturn(savedUser);

        UserDetails userDetails = User.withUsername(savedUser.getEmail())
            .password("encoded-password")
            .authorities("ROLE_USER")
            .build();
        when(userDetailsService.loadUserByUsername(savedUser.getEmail())).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertEquals(Role.USER, response.getRole());
        
        // Verify that the user builder was called with Role.USER
        org.mockito.ArgumentCaptor<com.ticketing.user.model.User> userCaptor = org.mockito.ArgumentCaptor.forClass(com.ticketing.user.model.User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.USER, userCaptor.getValue().getRole());
    }


    @Test
    @DisplayName("login with valid credentials should return auth response")
    void login_withValidCredentials_shouldReturnAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password@123");

        com.ticketing.user.model.User existing = com.ticketing.user.model.User.builder()
            .id(21L)
            .email(request.getEmail())
            .role(Role.ORGANIZER)
            .build();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(existing));

        UserDetails userDetails = User.withUsername(existing.getEmail())
            .password("encoded-password")
            .authorities("ROLE_ORGANIZER")
            .build();
        when(userDetailsService.loadUserByUsername(existing.getEmail())).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("jwt-login-token");

        AuthResponse response = authService.login(request);

        assertEquals("jwt-login-token", response.getToken());
        assertEquals(21L, response.getUserId());
        assertEquals(existing.getEmail(), response.getEmail());
        assertEquals(Role.ORGANIZER, response.getRole());
    }

    @Test
    @DisplayName("getUserIdByEmail with unknown email should throw not found")
    void getUserIdByEmail_withUnknownEmail_shouldThrowNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> authService.getUserIdByEmail("missing@example.com"));
    }

    private RegisterRequest createRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password@123");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setRole(Role.USER);
        return request;
    }
}
