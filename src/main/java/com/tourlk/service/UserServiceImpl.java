package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.ChangePasswordRequestDto;
import com.tourlk.dto.LicenceDocumentDownload;
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
import com.tourlk.repo.AccommodationRepository;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.NotificationRepository;
import com.tourlk.repo.PasswordResetTokenRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.ReviewRepository;
import com.tourlk.repo.RoomReservationRepository;
import com.tourlk.repo.SavedPaymentMethodRepository;
import com.tourlk.repo.SupportTicketRepository;
import com.tourlk.repo.TourPackageRepository;
import com.tourlk.repo.UserRepository;
import com.tourlk.repo.VehicleHireRepository;
import com.tourlk.repo.VehicleRepository;
import com.tourlk.security.JwtUtil;
import com.tourlk.util.LicenceRules;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final BookingRepository bookingRepository;
    private final VehicleHireRepository vehicleHireRepository;
    private final RoomReservationRepository roomReservationRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final VehicleRepository vehicleRepository;
    private final AccommodationRepository accommodationRepository;
    private final TourPackageRepository tourPackageRepository;
    private final LicenceDocumentStorage licenceDocumentStorage;

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

        String blockers = describeBlockingRecords(id);
        if (blockers != null) {
            throw new BadRequestException("This user still has " + blockers
                    + " and cannot be deleted. Keep the account deactivated instead.");
        }

        // The user's own throwaway rows go first; they would otherwise trip the FKs to users.
        notificationRepository.deleteByRecipientId(id);
        passwordResetTokenRepository.deleteByUserId(id);
        savedPaymentMethodRepository.findByUserIdOrderByCreatedAtDesc(id).forEach(this::detachFromGateway);
        savedPaymentMethodRepository.deleteByUserId(id);

        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException(
                    "This user still has other linked records and cannot be deleted. "
                            + "Keep the account deactivated instead.");
        }
    }

    /** Best effort: the card row is going away regardless, so a gateway hiccup must not block the delete. */
    private void detachFromGateway(com.tourlk.entity.SavedPaymentMethod card) {
        try {
            PaymentMethod.retrieve(card.getStripePaymentMethodId()).detach();
        } catch (StripeException | RuntimeException e) {
            log.warn("Could not detach Stripe payment method {} while deleting user: {}",
                    card.getStripePaymentMethodId(), e.getMessage());
        }
    }

    /** e.g. "2 bookings and 1 payment", or null when nothing blocks deletion. */
    private String describeBlockingRecords(Long userId) {
        List<String> parts = new ArrayList<>();
        addCount(parts, bookingRepository.countByTouristId(userId), "booking", "bookings");
        addCount(parts, vehicleHireRepository.countByTouristId(userId), "vehicle hire", "vehicle hires");
        addCount(parts, roomReservationRepository.countByTouristId(userId), "room reservation", "room reservations");
        addCount(parts, paymentRepository.countByPayerId(userId), "payment", "payments");
        addCount(parts, reviewRepository.countByReviewerId(userId), "review", "reviews");
        addCount(parts, supportTicketRepository.countByRaisedById(userId), "support ticket", "support tickets");
        addCount(parts, vehicleRepository.countByDriverId(userId), "vehicle", "vehicles");
        addCount(parts, accommodationRepository.countByOwnerId(userId), "accommodation", "accommodations");
        addCount(parts, tourPackageRepository.countByCreatedById(userId), "tour package", "tour packages");
        if (parts.isEmpty()) {
            return null;
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private void addCount(List<String> parts, long count, String singular, String plural) {
        if (count > 0) {
            parts.add(count + " " + (count == 1 ? singular : plural));
        }
    }

    // ------------------------------------------------------------------
    // Licence verification (GUIDE / DRIVER)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UserResponseDto submitLicence(User currentUser, LicenceSubmitRequestDto request, MultipartFile file) {
        if (currentUser.getRole() != Role.GUIDE && currentUser.getRole() != Role.DRIVER) {
            throw new BadRequestException("Only guides and drivers can submit licence details");
        }

        User user = getById(currentUser.getId());
        LocalDate today = LocalDate.now();
        if (user.getVerificationStatus() == VerificationStatus.VERIFIED && !LicenceRules.canRenew(user, today)) {
            throw new BadRequestException("Your licence is still valid until " + user.getLicenceExpiry());
        }

        licenceDocumentStorage.validate(file);
        String oldPath = user.getLicenceDocumentPath();
        String newPath = licenceDocumentStorage.store(user.getId(), file);

        user.setLicenceNumber(request.getLicenceNumber().trim());
        user.setLicenceExpiry(request.getLicenceExpiry());
        user.setLicenceDocumentPath(newPath);
        user.setLicenceDocumentUrl(null);
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setLicenceRejectionReason(null);
        user.setLicenceVerifiedById(null);
        user.setLicenceVerifiedAt(null);

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (RuntimeException e) {
            licenceDocumentStorage.deleteQuietly(newPath);
            throw e;
        }

        if (oldPath != null) {
            runAfterCommit(() -> licenceDocumentStorage.deleteQuietly(oldPath));
        }

        notificationService.notifyAdmins(NotificationType.LICENCE_SUBMITTED, "Licence submitted for verification",
                saved.getName() + " (" + saved.getRole() + ") submitted their licence for verification",
                "/admin/verifications");

        return toResponse(saved);
    }

    @Override
    public LicenceDocumentDownload getLicenceDocument(Long userId, User requester) {
        if (requester.getRole() != Role.ADMIN && !requester.getId().equals(userId)) {
            throw new AccessDeniedException("You can only view your own licence document");
        }
        User user = getById(userId);
        String path = user.getLicenceDocumentPath();
        if (path == null) {
            throw new ResourceNotFoundException("No licence document has been uploaded");
        }
        String contentType = licenceDocumentStorage.contentTypeOf(path);
        String extension = path.substring(path.lastIndexOf('.'));
        return new LicenceDocumentDownload(licenceDocumentStorage.load(path), "licence" + extension, contentType);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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
                .licenceDocumentUploaded(user.getLicenceDocumentPath() != null)
                .licenceExpired(LicenceRules.isExpired(user, LocalDate.now()))
                .licenceDaysUntilExpiry(LicenceRules.daysUntilExpiry(user, LocalDate.now()))
                .licenceRejectionReason(user.getLicenceRejectionReason())
                .licenceVerifiedAt(user.getLicenceVerifiedAt())
                .build();
    }

}
