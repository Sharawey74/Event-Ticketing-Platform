package com.ticketing.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.common.security.JwtService;
import com.ticketing.user.dto.AuthResponse;
import com.ticketing.user.dto.LoginRequest;
import com.ticketing.user.dto.RegisterRequest;
import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email is already registered");
        }

        Role role = request.getRole() == null ? Role.USER : request.getRole();

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .role(role)
            .build();

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ValidationException("Email is already registered");
        }
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(saved.getEmail());
        String token = jwtService.generateToken(userDetails);

        logger.info("User {} registered with role {}", saved.getEmail(), saved.getRole());
        return AuthResponse.builder()
            .token(token)
            .userId(saved.getId())
            .email(saved.getEmail())
            .role(saved.getRole())
            .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.getEmail()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        logger.info("User {} logged in", user.getEmail());
        return AuthResponse.builder()
            .token(token)
            .userId(user.getId())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }

    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(User::getId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
    }
}
