package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.LoginRequestDto;
import com.tourlk.dto.RegisterRequestDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.UserStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.repo.UserRepository;
import com.tourlk.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl}: registration (including the
 * duplicate-email guard) and login (including bad-credentials handling).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void register_newEmail_hashesPasswordPersistsUserAndReturnsToken() {
        RegisterRequestDto request = new RegisterRequestDto(
                "Tess Tourist", "tourist@example.com", "password123", "0770000000", Role.TOURIST);
        when(userRepository.existsByEmail("tourist@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(jwtUtil.generateToken("tourist@example.com", "TOURIST")).thenReturn("jwt-token");

        AuthResponseDto response = authService.register(request);

        assertThat(response.getUserId()).isEqualTo(42L);
        assertThat(response.getEmail()).isEqualTo("tourist@example.com");
        assertThat(response.getRole()).isEqualTo(Role.TOURIST);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed-pw");
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void register_emailAlreadyExists_throwsBadRequestAndNeverPersists() {
        RegisterRequestDto request = new RegisterRequestDto(
                "Dupe", "taken@example.com", "password123", null, Role.TOURIST);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void login_validCredentials_authenticatesAndReturnsToken() {
        LoginRequestDto request = new LoginRequestDto("tourist@example.com", "password123");
        User user = User.builder()
                .id(7L).name("Tess").email("tourist@example.com").password("hashed-pw").role(Role.TOURIST).build();
        when(userRepository.findByEmail("tourist@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("tourist@example.com", "TOURIST")).thenReturn("jwt-token");

        AuthResponseDto response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUserId()).isEqualTo(7L);
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("tourist@example.com", "password123"));
    }

    @Test
    void login_badCredentials_throwsBadCredentialsAndNeverIssuesToken() {
        LoginRequestDto request = new LoginRequestDto("tourist@example.com", "wrong");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(jwtUtil, never()).generateToken(any(), any());
    }

    @Test
    void login_userMissingAfterSuccessfulAuth_throwsBadCredentials() {
        LoginRequestDto request = new LoginRequestDto("ghost@example.com", "password123");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
