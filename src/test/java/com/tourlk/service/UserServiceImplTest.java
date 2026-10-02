package com.tourlk.service;

import com.tourlk.dto.ChangePasswordRequestDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.verifyNoInteractions;
import com.tourlk.dto.AuthResponseDto;
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
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.NotificationRepository;
import com.tourlk.repo.PasswordResetTokenRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.SavedPaymentMethodRepository;
import com.tourlk.repo.UserRepository;
import com.tourlk.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the profile and admin user-management parts of
 * {@link UserServiceImpl}. Repository and JwtUtil mocked.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private SavedPaymentMethodRepository savedPaymentMethodRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentRepository paymentRepository;
    // Remaining record repositories default to count 0 (unstubbed mocks).
    @Mock
    private com.tourlk.repo.VehicleHireRepository vehicleHireRepository;
    @Mock
    private com.tourlk.repo.RoomReservationRepository roomReservationRepository;
    @Mock
    private com.tourlk.repo.ReviewRepository reviewRepository;
    @Mock
    private com.tourlk.repo.SupportTicketRepository supportTicketRepository;
    @Mock
    private com.tourlk.repo.VehicleRepository vehicleRepository;
    @Mock
    private com.tourlk.repo.AccommodationRepository accommodationRepository;
    @Mock
    private com.tourlk.repo.TourPackageRepository tourPackageRepository;

    @InjectMocks
    private UserServiceImpl service;

    private User tourist;
    private User admin;
    private User guide;

    @BeforeEach
    void setUp() {
        tourist = User.builder().id(1L).name("Tess").email("tess@example.com")
                .role(Role.TOURIST).status(UserStatus.ACTIVE).build();
        admin = User.builder().id(9L).name("Amy").email("amy@example.com")
                .role(Role.ADMIN).status(UserStatus.ACTIVE).build();
        guide = User.builder().id(2L).name("Gina Guide").email("gina@example.com")
                .role(Role.GUIDE).status(UserStatus.ACTIVE)
                .verificationStatus(VerificationStatus.NOT_SUBMITTED).build();
    }

    // ------------------------------------------------------------------
    // updateProfile
    // ------------------------------------------------------------------

    @Test
    void updateProfile_newEmail_savesTrimmedValuesAndReturnsFreshToken() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken("new@example.com", "TOURIST")).thenReturn("fresh-token");

        AuthResponseDto result = service.updateProfile(tourist,
                new UpdateProfileRequestDto("  Tess T  ", " new@example.com ", "   "));

        assertThat(result.getName()).isEqualTo("Tess T");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getToken()).isEqualTo("fresh-token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(tourist.getPhone()).isNull();
    }

    @Test
    void updateProfile_emailBelongsToAnotherAccount_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("amy@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateProfile(tourist,
                new UpdateProfileRequestDto("Tess", "amy@example.com", null)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_keepingOwnEmail_isAllowed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("tess@example.com")).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken("tess@example.com", "TOURIST")).thenReturn("tok");

        AuthResponseDto result = service.updateProfile(tourist,
                new UpdateProfileRequestDto("Tess", "tess@example.com", "0771234567"));

        assertThat(result.getEmail()).isEqualTo("tess@example.com");
        assertThat(tourist.getPhone()).isEqualTo("0771234567");
    }

    // ------------------------------------------------------------------
    // deactivate / reactivate
    // ------------------------------------------------------------------

    @Test
    void deactivateUser_ownAccount_throwsBadRequest() {
        assertThatThrownBy(() -> service.deactivateUser(9L, admin))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateUser_otherAccount_marksDeactivated() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponseDto result = service.deactivateUser(1L, admin);

        assertThat(result.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void reactivateUser_marksActive() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.reactivateUser(1L).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ------------------------------------------------------------------
    // deleteUser
    // ------------------------------------------------------------------

    @Test
    void deleteUser_ownAccount_throwsBadRequest() {
        assertThatThrownBy(() -> service.deleteUser(9L, admin))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteUser_stillActive_throwsInvalidStatusTransition() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));

        assertThatThrownBy(() -> service.deleteUser(1L, admin))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_deactivated_deletes() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));

        service.deleteUser(1L, admin);

        verify(userRepository).delete(tourist);
        verify(userRepository).flush();
    }

    @Test
    void deleteUser_deactivatedWithOnlyNotifications_clearsThrowawayRowsThenDeletes() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));

        service.deleteUser(1L, admin);

        verify(notificationRepository).deleteByRecipientId(1L);
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(savedPaymentMethodRepository).deleteByUserId(1L);
        verify(userRepository).delete(tourist);
    }

    @Test
    void deleteUser_withBooking_failsNamingTheBlockingRecords() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(bookingRepository.countByTouristId(1L)).thenReturn(2L);
        when(paymentRepository.countByPayerId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteUser(1L, admin))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 bookings and 1 payment");
        verify(userRepository, never()).delete(any());
        verify(notificationRepository, never()).deleteByRecipientId(any());
    }

    @Test
    void deleteUser_referencedByOtherRecords_throwsBadRequest() {
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        doThrow(new DataIntegrityViolationException("FK")).when(userRepository).flush();

        assertThatThrownBy(() -> service.deleteUser(1L, admin))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be deleted");
    }

    // ------------------------------------------------------------------
    // searchUsers
    // ------------------------------------------------------------------

    @Test
    void searchUsers_blankTerm_listsEveryone() {
        when(userRepository.findAll(any(Sort.class))).thenReturn(List.of(tourist, admin));

        assertThat(service.searchUsers("  ")).hasSize(2);
    }

    @Test
    void searchUsers_term_searchesNameOrEmail() {
        when(userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                any(), any(), any(Sort.class))).thenReturn(List.of(tourist));

        List<UserResponseDto> result = service.searchUsers(" tess ");

        assertThat(result).extracting(UserResponseDto::getEmail).containsExactly("tess@example.com");
    }

    // ------------------------------------------------------------------
    // Licence verification
    // ------------------------------------------------------------------

    @Test
    void submitLicence_asGuide_setsPendingAndNotifiesAdmins() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(guide));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponseDto result = service.submitLicence(guide,
                new LicenceSubmitRequestDto("DL-12345", LocalDate.now().plusYears(1), "https://docs.example.com/dl.pdf"));

        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(result.getLicenceNumber()).isEqualTo("DL-12345");
        verify(notificationService).notifyAdmins(any(NotificationType.class), any(), any(), any());
    }

    @Test
    void submitLicence_asTourist_throwsBadRequest() {
        assertThatThrownBy(() -> service.submitLicence(tourist,
                new LicenceSubmitRequestDto("DL-1", LocalDate.now().plusYears(1), "https://docs.example.com/dl.pdf")))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyLicence_pending_marksVerifiedAndNotifiesUser() {
        guide.setVerificationStatus(VerificationStatus.PENDING);
        when(userRepository.findById(2L)).thenReturn(Optional.of(guide));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponseDto result = service.verifyLicence(2L, admin);

        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(notificationService).notify(any(User.class), eq(NotificationType.LICENCE_VERIFIED), any(), any(), any());
    }

    @Test
    void verifyLicence_notPending_throwsInvalidStatusTransition() {
        guide.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(guide));

        assertThatThrownBy(() -> service.verifyLicence(2L, admin))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectLicence_pending_setsRejectedWithReason() {
        guide.setVerificationStatus(VerificationStatus.PENDING);
        when(userRepository.findById(2L)).thenReturn(Optional.of(guide));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponseDto result = service.rejectLicence(2L, "Blurry document", admin);

        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(result.getLicenceRejectionReason()).isEqualTo("Blurry document");
        verify(notificationService).notify(any(User.class), eq(NotificationType.LICENCE_REJECTED), any(), any(), any());
    }

    @Test
    void rejectLicence_blankReason_throwsBadRequest() {
        assertThatThrownBy(() -> service.rejectLicence(2L, "  ", admin))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // self-service deactivation
    // ------------------------------------------------------------------

    @Test
    void deactivateOwnAccount_correctPassword_deactivatesAndNotifiesAdmins() {
        tourist.setPassword("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        service.deactivateOwnAccount(tourist, "secret123");

        assertThat(tourist.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userRepository).save(tourist);
        verify(notificationService).notifyAdmins(eq(NotificationType.ACCOUNT_DEACTIVATED), any(), any(), any());
    }

    @Test
    void deactivateOwnAccount_wrongPassword_throwsBadRequestAndChangesNothing() {
        tourist.setPassword("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(passwordEncoder.matches("nope", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deactivateOwnAccount(tourist, "nope"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("incorrect");
        assertThat(tourist.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void deactivateOwnAccount_alreadyDeactivated_throwsInvalidStatusTransition() {
        tourist.setPassword("hash");
        tourist.setStatus(UserStatus.DEACTIVATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.deactivateOwnAccount(tourist, "secret123"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void deactivateOwnAccount_lastActiveAdmin_isRefused() {
        admin.setPassword("hash");
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.deactivateOwnAccount(admin, "secret123"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("only active admin");
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deactivateOwnAccount_adminWhenAnotherAdminExists_isAllowed() {
        admin.setPassword("hash");
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

        service.deactivateOwnAccount(admin, "secret123");

        assertThat(admin.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    // ------------------------------------------------------------------
    // account-change notifications
    // ------------------------------------------------------------------

    @Test
    void updateProfile_notifiesTheUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("tess@example.com")).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken(any(), any())).thenReturn("tok");

        service.updateProfile(tourist, new UpdateProfileRequestDto("Tess", "tess@example.com", null));

        verify(notificationService).notify(eq(tourist), eq(NotificationType.ACCOUNT_PROFILE_UPDATED), any(), any(), any());
    }

    @Test
    void changePassword_notifiesTheUser() {
        tourist.setPassword("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(passwordEncoder.matches("oldpass12", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("new-hash");

        service.changePassword(tourist, new ChangePasswordRequestDto("oldpass12", "newpass123"));

        verify(notificationService).notify(eq(tourist), eq(NotificationType.ACCOUNT_PASSWORD_CHANGED), any(), any(), any());
    }

    @Test
    void adminDeactivateAndReactivate_notifyTheAffectedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(tourist));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivateUser(1L, admin);
        service.reactivateUser(1L);

        verify(notificationService).notify(eq(tourist), eq(NotificationType.ACCOUNT_DEACTIVATED), any(), any(), any());
        verify(notificationService).notify(eq(tourist), eq(NotificationType.ACCOUNT_REACTIVATED), any(), any(), any());
    }
}
