package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.ChangePasswordRequestDto;
import com.tourlk.dto.LicenceSubmitRequestDto;
import com.tourlk.dto.UpdateProfileRequestDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.UserStatus;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.UserRepository;
import com.tourlk.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

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

        boolean emailChanged = !email.equalsIgnoreCase(currentUser.getEmail());
        User saved = userRepository.save(user);

        notificationService.notify(saved, NotificationType.ACCOUNT_PROFILE_UPDATED, "Profile updated",
                emailChanged ? "Your profile and sign-in email were updated. If this was not you, contact support."
                        : "Your profile details were updated.", "/profile");

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
    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequestDto request) {
        User user = getById(currentUser.getId());
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        notificationService.notify(user, NotificationType.ACCOUNT_PASSWORD_CHANGED, "Password changed",
                "Your password was changed. If this was not you, contact support immediately.", "/profile");
    }

    @Override
    @Transactional
    public void deactivateOwnAccount(User currentUser, String password) {
        User user = getById(currentUser.getId());
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("Password is incorrect");
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw new InvalidStatusTransitionException("This account is already deactivated");
        }
        if (user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE
                && userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BadRequestException("You are the only active admin, so you cannot deactivate your account");
        }

        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);

        notificationService.notifyAdmins(NotificationType.ACCOUNT_DEACTIVATED, "Account deactivated by its owner",
                user.getName() + " (" + user.getRole() + ") deactivated their own account", "/admin/users");
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
        User saved = userRepository.save(user);

        notificationService.notify(saved, NotificationType.ACCOUNT_DEACTIVATED, "Account deactivated",
                "Your account was deactivated by an administrator. Contact support if you think this is a mistake.",
                "/support/new");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponseDto reactivateUser(Long id) {
        User user = getById(id);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);

        notificationService.notify(saved, NotificationType.ACCOUNT_REACTIVATED, "Account reactivated",
                "Your account was reactivated. Welcome back!", "/profile");
        return toResponse(saved);
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

    // ------------------------------------------------------------------
    // Licence verification (GUIDE / DRIVER)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UserResponseDto submitLicence(User currentUser, LicenceSubmitRequestDto request) {
        if (currentUser.getRole() != Role.GUIDE && currentUser.getRole() != Role.DRIVER) {
            throw new BadRequestException("Only guides and drivers can submit licence details");
        }

        User user = getById(currentUser.getId());
        user.setLicenceNumber(request.getLicenceNumber().trim());
        user.setLicenceExpiry(request.getLicenceExpiry());
        user.setLicenceDocumentUrl(request.getLicenceDocumentUrl().trim());
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setLicenceRejectionReason(null);
        user.setLicenceVerifiedById(null);
        user.setLicenceVerifiedAt(null);

        User saved = userRepository.save(user);

        notificationService.notifyAdmins(NotificationType.LICENCE_SUBMITTED, "Licence submitted for verification",
                saved.getName() + " (" + saved.getRole() + ") submitted their licence for verification",
                "/admin/verifications");

        return toResponse(saved);
    }

    @Override
    public List<UserResponseDto> getPendingLicences() {
        return userRepository.findByVerificationStatus(VerificationStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserResponseDto verifyLicence(Long userId, User admin) {
        User user = getById(userId);
        assertLicencePending(user);

        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setLicenceRejectionReason(null);
        user.setLicenceVerifiedById(admin.getId());
        user.setLicenceVerifiedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        notificationService.notify(saved, NotificationType.LICENCE_VERIFIED, "Licence verified",
                "Your licence has been verified. You can now list " +
                        (saved.getRole() == Role.DRIVER ? "vehicles" : "tour packages") + ".",
                "/profile");

        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponseDto rejectLicence(Long userId, String reason, User admin) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection reason is required");
        }
        User user = getById(userId);
        assertLicencePending(user);

        user.setVerificationStatus(VerificationStatus.REJECTED);
        user.setLicenceRejectionReason(reason.trim());
        user.setLicenceVerifiedById(admin.getId());
        user.setLicenceVerifiedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        notificationService.notify(saved, NotificationType.LICENCE_REJECTED, "Licence rejected",
                "Your licence submission was not approved. Reason: " + saved.getLicenceRejectionReason(),
                "/profile");

        return toResponse(saved);
    }

    private void assertLicencePending(User user) {
        if (user.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new InvalidStatusTransitionException(
                    "Only a PENDING licence submission can be reviewed, but this one is "
                            + user.getVerificationStatus());
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
                .verificationStatus(user.getVerificationStatus())
                .licenceNumber(user.getLicenceNumber())
                .licenceExpiry(user.getLicenceExpiry())
                .licenceDocumentUrl(user.getLicenceDocumentUrl())
                .licenceRejectionReason(user.getLicenceRejectionReason())
                .licenceVerifiedAt(user.getLicenceVerifiedAt())
                .build();
    }

}
