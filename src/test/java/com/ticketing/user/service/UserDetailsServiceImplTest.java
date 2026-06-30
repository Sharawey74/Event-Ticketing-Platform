package com.ticketing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("loadUserByUsername: should return CustomUserDetails when user exists")
    void loadUserByUsername_whenUserExists_shouldReturnCustomUserDetails() {
        User user = User.builder()
                .id(5L)
                .email("test@example.com")
                .passwordHash("hash123")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(details).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) details).getId()).isEqualTo(5L);
        assertThat(details.getUsername()).isEqualTo("test@example.com");
        assertThat(details.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("loadUserByUsername: should throw UsernameNotFoundException when user not found")
    void loadUserByUsername_whenUserNotFound_shouldThrowUsernameNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
