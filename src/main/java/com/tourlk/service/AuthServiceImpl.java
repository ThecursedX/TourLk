package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.LoginRequestDto;
import com.tourlk.dto.RegisterRequestDto;
import com.tourlk.entity.User;
import com.tourlk.enums.UserStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.repo.UserRepository;
import com.tourlk.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Override
    public AuthResponseDto register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return toResponse(user, token);
    }

    @Override
    public AuthResponseDto login(LoginRequestDto request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        return toResponse(user, token);
    }

    private AuthResponseDto toResponse(User user, String token) {
        return AuthResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .token(token)
                .tokenType("Bearer")
                .build();
    }

}
