package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.UpdateProfileRequestDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.UserStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.UserRepository;
import com.tourlk.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public UserResponseDto getProfile(User currentUser) {
        return toResponse(currentUser);
    }

    @Override
    @Transactional
    public AuthResponseDto updateProfile(User currentUser, UpdateProfileRequestDto request) {
        User user = getById(currentUser.getId());
        String email = request.getEmail().trim();

        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("An account with this email already exists");
                });

        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank()
                ? null
                : request.getPhone().trim());

        User saved = userRepository.save(user);

        return AuthResponseDto.builder()
                .userId(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .role(saved.getRole())
                .token(jwtUtil.generateToken(saved.getEmail(), saved.getRole().name()))
                .tokenType("Bearer")
                .build();
    }

    @Override
    public List<UserResponseDto> searchUsers(String search) {
        Sort byId = Sort.by(Sort.Direction.ASC, "id");
        List<User> users = search == null || search.isBlank()
                ? userRepository.findAll(byId)
                : userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search.trim(), search.trim(), byId);
        return users.stream().map(this::toResponse).toList();
    }

    @Override
    public UserResponseDto getUserById(Long id) {
        return toResponse(getById(id));
    }

    @Override
    @Transactional
    public UserResponseDto deactivateUser(Long id, User currentUser) {
        if (currentUser.getId().equals(id)) {
            throw new BadRequestException("You cannot deactivate your own account");
        }
        User user = getById(id);
        user.setStatus(UserStatus.DEACTIVATED);
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponseDto reactivateUser(Long id) {
        User user = getById(id);
        user.setStatus(UserStatus.ACTIVE);
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id, User currentUser) {
        if (currentUser.getId().equals(id)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User user = getById(id);
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw new InvalidStatusTransitionException("Only deactivated accounts can be deleted");
        }
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException(
                    "This user still has bookings, listings or other records and cannot be deleted. "
                            + "Keep the account deactivated instead.");
        }
    }

    private UserResponseDto toResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
