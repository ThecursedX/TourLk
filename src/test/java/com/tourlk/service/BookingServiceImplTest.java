package com.tourlk.service;

import com.tourlk.config.CancellationPolicyProperties;
import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.entity.PackageDeparture;
import com.tourlk.entity.Payment;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.CapacityExceededException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.PackageDepartureRepository;
import com.tourlk.repo.PaymentRepository;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookingServiceImpl}: create/confirm/cancel/complete
 * plus the ownership and status-transition guards. Repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private PackageDepartureRepository departureRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundGateway refundGateway;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.tourlk.repo.PackageAddOnRepository addOnRepository;
    @Mock
    private RoomReservationService roomReservationService;
    @Mock
    private VehicleHireService vehicleHireService;
    /** Real policy (7 / 3 days, 50%) — it's pure logic, nothing to mock. */
    @Spy
    private CancellationPolicy cancellationPolicy = new CancellationPolicy(new CancellationPolicyProperties(7, 3, 50));

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User tourist;
    private User otherTourist;
    private User admin;
    private User guide;
    private TourPackage activePackage;

    @BeforeEach
    void setUp() {
        tourist = User.builder().id(1L).name("Tess").email("tess@example.com").role(Role.TOURIST).build();
        otherTourist = User.builder().id(2L).name("Otto").email("otto@example.com").role(Role.TOURIST).build();
        admin = User.builder().id(9L).name("Amy Admin").email("admin@example.com").role(Role.ADMIN).build();
        guide = User.builder().id(3L).name("Gina Guide").email("gina@example.com").role(Role.GUIDE).build();
        activePackage = TourPackage.builder()
                .id(100L).title("Hill Country")
                .destination(Destination.builder()
                        .id(1L).name("Kandy").region("Central Province").status(DestinationStatus.PUBLISHED).build())
                .price(new BigDecimal("120.00")).maxCapacity(10).status(PackageStatus.ACTIVE)
                .createdBy(guide)
                .build();
    }

    private Booking booking(BookingStatus status, User owner) {
        return Booking.builder()
                .id(5L).tourist(owner).tourPackage(activePackage)
                .travelDate(LocalDate.now().plusDays(30)).numberOfTravelers(2)
                .status(status)
                .build();
    }

    private Payment payment(PaymentStatus status, String amount) {
        return Payment.builder()
                .id(55L).payer(tourist).payableType(PayableType.BOOKING).payableId(5L)
                .amount(new BigDecimal(amount)).currency("usd").stripePaymentIntentId("pi_1")
                .status(status)
                .build();
    }

    // ------------------------------------------------------------------
    // createBooking
    // ------------------------------------------------------------------

    @Test
    void createBooking_activePackageWithCapacity_savesPendingBooking() {
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(0);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(5L);
            return b;
        });

        BookingRequestDto request = new BookingRequestDto(100L, LocalDate.now().plusDays(30), 2, "Window seat");
        BookingResponseDto result = bookingService.createBooking(request, tourist);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getTouristId()).isEqualTo(1L);
        assertThat(result.getTourPackage().getId()).isEqualTo(100L);
    }

    @Test
    void createBooking_packageNotActive_throwsBadRequest() {
        activePackage.setStatus(PackageStatus.INACTIVE);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));

        BookingRequestDto request = new BookingRequestDto(100L, LocalDate.now().plusDays(30), 2, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, tourist))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_wouldExceedCapacity_throwsCapacityExceeded() {
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(9);

        BookingRequestDto request = new BookingRequestDto(100L, LocalDate.now().plusDays(30), 2, null); // 9 + 2 > 10

        assertThatThrownBy(() -> bookingService.createBooking(request, tourist))
                .isInstanceOf(CapacityExceededException.class);
        verify(bookingRepository, never()).save(any());
    }

    // --- destination closure window ---

    private static final LocalDate TODAY = LocalDate.now();

    private void closeDestination(LocalDate from, LocalDate until, int durationDays) {
        Destination destination = activePackage.getDestination();
        destination.setStatus(DestinationStatus.TEMPORARILY_CLOSED);
        destination.setClosureFrom(from);
        destination.setClosureUntil(until);
        activePackage.setDurationDays(durationDays);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
    }

    private void stubBookingSave() {
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(0);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private BookingRequestDto requestFor(LocalDate travelDate) {
        return new BookingRequestDto(100L, travelDate, 2, null);
    }

    @Test
    void createBooking_tripInsideClosureWindow_isBlockedWithClearMessage() {
        LocalDate from = TODAY.plusDays(10);
        LocalDate until = TODAY.plusDays(20);
        closeDestination(from, until, 3);

        assertThatThrownBy(() -> bookingService.createBooking(requestFor(TODAY.plusDays(12)), tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Kandy is closed from " + from + " to " + until
                        + ", so this package can't be booked for those dates.");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_tripOutsideClosureWindow_isAllowed() {
        closeDestination(TODAY.plusDays(10), TODAY.plusDays(20), 3);
        stubBookingSave();

        assertThat(bookingService.createBooking(requestFor(TODAY.plusDays(30)), tourist).getStatus())
                .isEqualTo(BookingStatus.PENDING);
        // Ends the day before the closure starts: 3 days from D+7 = D+7..D+9.
        assertThat(bookingService.createBooking(requestFor(TODAY.plusDays(7)), tourist).getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_tripPartiallyOverlappingTheStart_isBlocked() {
        closeDestination(TODAY.plusDays(10), TODAY.plusDays(20), 3);

        // D+8..D+10: its last day is the first closed day.
        assertThatThrownBy(() -> bookingService.createBooking(requestFor(TODAY.plusDays(8)), tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is closed from");
    }

    @Test
    void createBooking_tripPartiallyOverlappingTheEnd_isBlocked() {
        closeDestination(TODAY.plusDays(10), TODAY.plusDays(20), 3);

        // D+20..D+22 starts on the last closed day.
        assertThatThrownBy(() -> bookingService.createBooking(requestFor(TODAY.plusDays(20)), tourist))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createBooking_closureWithFromOnly_blocksEverythingFromThatDate() {
        closeDestination(TODAY.plusDays(10), null, 2);
        stubBookingSave();

        assertThatThrownBy(() -> bookingService.createBooking(requestFor(TODAY.plusDays(200)), tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("until further notice");
        assertThat(bookingService.createBooking(requestFor(TODAY.plusDays(5)), tourist).getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_closureWithUntilOnly_blocksFromTodayUntilThatDate() {
        closeDestination(null, TODAY.plusDays(10), 2);
        stubBookingSave();

        assertThatThrownBy(() -> bookingService.createBooking(requestFor(TODAY.plusDays(3)), tourist))
                .isInstanceOf(BadRequestException.class);
        assertThat(bookingService.createBooking(requestFor(TODAY.plusDays(11)), tourist).getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_closureWithNeitherDate_blocksEveryDate() {
        closeDestination(null, null, 2);

        assertThatThrownBy(() -> bookingService.createBooking(requestFor(TODAY.plusDays(90)), tourist))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requestReschedule_intoClosureWindow_isBlocked() {
        Booking booking = booking(BookingStatus.CONFIRMED, tourist);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        activePackage.setDurationDays(3);
        activePackage.getDestination().setStatus(DestinationStatus.TEMPORARILY_CLOSED);
        activePackage.getDestination().setClosureFrom(TODAY.plusDays(10));
        activePackage.getDestination().setClosureUntil(TODAY.plusDays(20));

        assertThatThrownBy(() -> bookingService.requestReschedule(5L,
                new RescheduleRequestDto(TODAY.plusDays(15)), tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Kandy is closed from");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_packageNotFound_throwsResourceNotFound() {
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.empty());

        BookingRequestDto request = new BookingRequestDto(100L, LocalDate.now().plusDays(30), 2, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, tourist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // confirmBooking
    // ------------------------------------------------------------------

    @Test
    void confirmBooking_pendingWithCapacity_setsConfirmed() {
        Booking pending = booking(BookingStatus.PENDING, tourist);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(0);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result = bookingService.confirmBooking(5L, guide);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmBooking_byUnrelatedUser_throwsAccessDenied() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThatThrownBy(() -> bookingService.confirmBooking(5L, otherTourist))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingAfterPayment_alreadyConfirmed_isNoOp() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));

        bookingService.confirmBookingAfterPayment(5L);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBooking_notPending_throwsInvalidStatusTransition() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));

        assertThatThrownBy(() -> bookingService.confirmBooking(5L, guide))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(bookingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // cancelBooking
    // ------------------------------------------------------------------

    @Test
    void cancelBooking_byOwner_setsCancelled() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result = bookingService.cancelBooking(5L, tourist);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_byAdmin_setsCancelled() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result = bookingService.cancelBooking(5L, admin);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_byUnrelatedTourist_throwsAccessDenied() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThatThrownBy(() -> bookingService.cancelBooking(5L, otherTourist))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_alreadyCompleted_throwsInvalidStatusTransition() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.COMPLETED, tourist)));

        assertThatThrownBy(() -> bookingService.cancelBooking(5L, tourist))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void cancelBooking_farEnoughOut_refundsInFull() {
        Booking active = booking(BookingStatus.CONFIRMED, tourist);
        active.setTravelDate(LocalDate.now().plusDays(30)); // >= 7 days -> 100%
        Payment succeeded = payment(PaymentStatus.SUCCEEDED, "200.00");
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(active));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(succeeded));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(5L, tourist);

        verify(refundGateway).refund(succeeded, new BigDecimal("200.00"));
    }

    @Test
    void cancelBooking_withinPartialWindow_refundsHalf() {
        Booking active = booking(BookingStatus.CONFIRMED, tourist);
        active.setTravelDate(LocalDate.now().plusDays(4)); // 3-6 days -> 50%
        Payment succeeded = payment(PaymentStatus.SUCCEEDED, "200.00");
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(active));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(succeeded));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(5L, tourist);

        verify(refundGateway).refund(succeeded, new BigDecimal("100.00"));
    }

    @Test
    void cancelBooking_tooCloseToTravel_noRefundIssued() {
        Booking active = booking(BookingStatus.CONFIRMED, tourist);
        active.setTravelDate(LocalDate.now().plusDays(1)); // < 3 days -> 0%
        Payment succeeded = payment(PaymentStatus.SUCCEEDED, "200.00");
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(active));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(succeeded));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(5L, tourist);

        verify(refundGateway, never()).refund(any(), any());
    }

    @Test
    void cancelBooking_uncompletedPayment_isMarkedCancelled() {
        Booking active = booking(BookingStatus.PENDING, tourist);
        Payment pending = payment(PaymentStatus.PENDING, "200.00");
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(active));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(pending));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(5L, tourist);

        verify(refundGateway).cancelUncompletedPayment(pending);
        verify(refundGateway, never()).refund(any(), any());
    }

    // ------------------------------------------------------------------
    // getCancellationPreview
    // ------------------------------------------------------------------

    @Test
    void getCancellationPreview_farEnoughOut_previews100PercentOfSucceededPayment() {
        Booking active = booking(BookingStatus.CONFIRMED, tourist);
        active.setTravelDate(LocalDate.now().plusDays(30));
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(active));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of(payment(PaymentStatus.SUCCEEDED, "200.00")));

        CancellationPreviewResponseDto preview = bookingService.getCancellationPreview(5L, tourist);

        assertThat(preview.getRefundPercent()).isEqualTo(100);
        assertThat(preview.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(preview.isHasPayment()).isTrue();
    }

    @Test
    void getCancellationPreview_noSucceededPayment_zeroRefundButRuleStillShown() {
        Booking active = booking(BookingStatus.PENDING, tourist);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(active));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L))
                .thenReturn(List.of());

        CancellationPreviewResponseDto preview = bookingService.getCancellationPreview(5L, tourist);

        assertThat(preview.isHasPayment()).isFalse();
        assertThat(preview.getRefundAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(preview.getRuleText()).isNotBlank();
    }

    @Test
    void getCancellationPreview_byUnrelatedTourist_throwsAccessDenied() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThatThrownBy(() -> bookingService.getCancellationPreview(5L, otherTourist))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------
    // rejectBooking
    // ------------------------------------------------------------------

    @Test
    void rejectBooking_pendingByPackageOwner_setsRejectedWithReason() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result = bookingService.rejectBooking(5L, "No availability", guide);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("No availability");
    }

    @Test
    void rejectBooking_pendingByAdmin_setsRejected() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));
        when(paymentRepository.findByPayableTypeAndPayableId(PayableType.BOOKING, 5L)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(bookingService.rejectBooking(5L, "Not available", admin).getStatus())
                .isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void rejectBooking_byUnrelatedGuide_throwsAccessDenied() {
        User otherGuide = User.builder().id(4L).name("Gary").role(Role.GUIDE).build();
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThatThrownBy(() -> bookingService.rejectBooking(5L, "reason", otherGuide))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectBooking_blankReason_throwsBadRequest() {
        assertThatThrownBy(() -> bookingService.rejectBooking(5L, "  ", guide))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectBooking_notPending_throwsInvalidStatusTransition() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));

        assertThatThrownBy(() -> bookingService.rejectBooking(5L, "reason", guide))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(bookingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // getBookingsByPackage
    // ------------------------------------------------------------------

    @Test
    void getBookingsByPackage_byOwner_returnsBookings() {
        when(tourPackageRepository.findById(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.findByTourPackageId(100L)).thenReturn(List.of(booking(BookingStatus.PENDING, tourist)));

        assertThat(bookingService.getBookingsByPackage(100L, guide)).hasSize(1);
    }

    @Test
    void getBookingsByPackage_byAdmin_returnsBookings() {
        when(tourPackageRepository.findById(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.findByTourPackageId(100L)).thenReturn(List.of());

        assertThat(bookingService.getBookingsByPackage(100L, admin)).isEmpty();
    }

    @Test
    void getBookingsByPackage_byUnrelatedGuide_throwsAccessDenied() {
        User otherGuide = User.builder().id(4L).name("Gary").role(Role.GUIDE).build();
        when(tourPackageRepository.findById(100L)).thenReturn(Optional.of(activePackage));

        assertThatThrownBy(() -> bookingService.getBookingsByPackage(100L, otherGuide))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------
    // completeBooking
    // ------------------------------------------------------------------

    @Test
    void completeBooking_confirmed_setsCompleted() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(bookingService.completeBooking(5L).getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void completeBooking_stillPending_throwsInvalidStatusTransition() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThatThrownBy(() -> bookingService.completeBooking(5L))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ------------------------------------------------------------------
    // getBookingById / requestReschedule — ownership
    // ------------------------------------------------------------------

    @Test
    void getBookingById_byOwner_returnsBooking() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThat(bookingService.getBookingById(5L, tourist).getId()).isEqualTo(5L);
    }

    @Test
    void getBookingById_byAdmin_returnsBooking() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThat(bookingService.getBookingById(5L, admin).getId()).isEqualTo(5L);
    }

    @Test
    void getBookingById_byPackageOwnerGuide_returnsBooking() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThat(bookingService.getBookingById(5L, guide).getId()).isEqualTo(5L);
    }

    @Test
    void getBookingById_byUnrelatedTourist_throwsAccessDenied() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));

        assertThatThrownBy(() -> bookingService.getBookingById(5L, otherTourist))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requestReschedule_byNonOwner_throwsAccessDenied() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));

        RescheduleRequestDto request = new RescheduleRequestDto(LocalDate.now().plusDays(60));

        assertThatThrownBy(() -> bookingService.requestReschedule(5L, request, otherTourist))
                .isInstanceOf(AccessDeniedException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void requestReschedule_byOwnerFromConfirmed_movesToRescheduleRequested() {
        Booking confirmed = booking(BookingStatus.CONFIRMED, tourist);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(confirmed));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        RescheduleRequestDto request = new RescheduleRequestDto(LocalDate.now().plusDays(60));
        BookingResponseDto result = bookingService.requestReschedule(5L, request, tourist);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.RESCHEDULE_REQUESTED);
        assertThat(confirmed.getStatusBeforeReschedule()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void getBookingById_notFound_throwsResourceNotFound() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingById(404L, tourist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Package departures
    // ------------------------------------------------------------------

    private PackageDeparture departureOn(LocalDate date, int seatsTotal) {
        return PackageDeparture.builder().id(70L).tourPackage(activePackage).departureDate(date).seatsTotal(seatsTotal)
                .build();
    }

    @Test
    void createBooking_packageWithDepartures_dateNotADeparture_throwsBadRequest() {
        LocalDate offSchedule = LocalDate.now().plusDays(31);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, offSchedule)).thenReturn(Optional.empty());
        when(departureRepository.existsByTourPackageId(100L)).thenReturn(true);

        BookingRequestDto request = new BookingRequestDto(100L, offSchedule, 2, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, tourist))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no departure on " + offSchedule);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_onDeparture_withinSeats_savesPendingBooking() {
        LocalDate departureDate = LocalDate.now().plusDays(30);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, departureDate))
                .thenReturn(Optional.of(departureOn(departureDate, 4)));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(eq(100L), eq(departureDate), any()))
                .thenReturn(2);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result =
                bookingService.createBooking(new BookingRequestDto(100L, departureDate, 2, null), tourist); // 2 + 2 <= 4

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getTravelDate()).isEqualTo(departureDate);
    }

    @Test
    void createBooking_onDeparture_seatLimitIsDepartureSeatsNotPackageCapacity() {
        LocalDate departureDate = LocalDate.now().plusDays(30);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage)); // maxCapacity 10
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, departureDate))
                .thenReturn(Optional.of(departureOn(departureDate, 4)));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(eq(100L), eq(departureDate), any()))
                .thenReturn(3);

        BookingRequestDto request = new BookingRequestDto(100L, departureDate, 2, null); // 3 + 2 > 4, though < 10

        assertThatThrownBy(() -> bookingService.createBooking(request, tourist))
                .isInstanceOf(CapacityExceededException.class)
                .hasMessageContaining("Only 1 spot(s) left");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_packageWithoutDepartures_anyDateUsesMaxCapacity() {
        LocalDate anyDate = LocalDate.now().plusDays(45);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(departureRepository.existsByTourPackageId(100L)).thenReturn(false);
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(8);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result =
                bookingService.createBooking(new BookingRequestDto(100L, anyDate, 2, null), tourist); // 8 + 2 <= 10

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void createBooking_locksPackageBeforeCheckingDepartures() {
        LocalDate departureDate = LocalDate.now().plusDays(30);
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, departureDate))
                .thenReturn(Optional.of(departureOn(departureDate, 4)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.createBooking(new BookingRequestDto(100L, departureDate, 1, null), tourist);

        InOrder order = inOrder(tourPackageRepository, departureRepository, bookingRepository);
        order.verify(tourPackageRepository).findByIdForUpdate(100L);
        order.verify(departureRepository).findByTourPackageIdAndDepartureDate(100L, departureDate);
        order.verify(bookingRepository).sumTravelersByPackageAndDateAndStatusIn(eq(100L), eq(departureDate), any());
        order.verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void confirmBooking_legacyDateNotADeparture_stillConfirmsAgainstMaxCapacity() {
        Booking pending = booking(BookingStatus.PENDING, tourist);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, pending.getTravelDate()))
                .thenReturn(Optional.empty());
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(0);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(bookingService.confirmBooking(5L, guide).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(departureRepository, never()).existsByTourPackageId(any());
    }

    @Test
    void requestReschedule_packageWithDepartures_newDateNotADeparture_throwsBadRequest() {
        Booking confirmed = booking(BookingStatus.CONFIRMED, tourist);
        LocalDate offSchedule = LocalDate.now().plusDays(60);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(confirmed));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, offSchedule)).thenReturn(Optional.empty());
        when(departureRepository.existsByTourPackageId(100L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.requestReschedule(5L, new RescheduleRequestDto(offSchedule), tourist))
                .isInstanceOf(BadRequestException.class);
        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void requestReschedule_toADeparture_movesToRescheduleRequested() {
        Booking confirmed = booking(BookingStatus.CONFIRMED, tourist);
        LocalDate departureDate = LocalDate.now().plusDays(60);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(confirmed));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, departureDate))
                .thenReturn(Optional.of(departureOn(departureDate, 10)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result =
                bookingService.requestReschedule(5L, new RescheduleRequestDto(departureDate), tourist);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.RESCHEDULE_REQUESTED);
        assertThat(result.getRequestedTravelDate()).isEqualTo(departureDate);
    }

    @Test
    void approveReschedule_departureRemovedSinceRequest_throwsBadRequest() {
        Booking requested = booking(BookingStatus.RESCHEDULE_REQUESTED, tourist);
        LocalDate removedDate = LocalDate.now().plusDays(60);
        requested.setRequestedTravelDate(removedDate);
        requested.setStatusBeforeReschedule(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(requested));
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(departureRepository.findByTourPackageIdAndDepartureDate(100L, removedDate)).thenReturn(Optional.empty());
        when(departureRepository.existsByTourPackageId(100L)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.approveReschedule(5L))
                .isInstanceOf(BadRequestException.class);
        assertThat(requested.getStatus()).isEqualTo(BookingStatus.RESCHEDULE_REQUESTED);
        verify(bookingRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // totalPrice
    // ------------------------------------------------------------------

    @Test
    void createBooking_storesTotalPriceFromCurrentPackagePrice() {
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage)); // 120.00
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponseDto result = bookingService.createBooking(
                new BookingRequestDto(100L, LocalDate.now().plusDays(30), 3, null), tourist);

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalPrice()).isEqualByComparingTo("360.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("360.00");
    }

    @Test
    void storedTotalPrice_isUnaffectedByLaterPackagePriceChange() {
        Booking existing = booking(BookingStatus.PENDING, tourist); // 2 travelers
        existing.setTotalPrice(new BigDecimal("240.00"));
        activePackage.setPrice(new BigDecimal("999.00"));
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThat(bookingService.getBookingById(5L, tourist).getTotalPrice()).isEqualByComparingTo("240.00");
    }

    @Test
    void legacyBookingWithoutTotalPrice_fallsBackToLivePackagePrice() {
        Booking legacy = booking(BookingStatus.PENDING, tourist); // totalPrice null, 2 travelers
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(legacy));

        assertThat(bookingService.getBookingById(5L, tourist).getTotalPrice()).isEqualByComparingTo("240.00");
    }

    // ------------------------------------------------------------------
    // add-ons (rooms / vehicles attached to the package)
    // ------------------------------------------------------------------

    private void attachAddOns() {
        com.tourlk.entity.PackageAddOn roomAddOn = com.tourlk.entity.PackageAddOn.builder()
                .tourPackage(activePackage).room(com.tourlk.entity.Room.builder().id(7L).build()).build();
        com.tourlk.entity.PackageAddOn vehicleAddOn = com.tourlk.entity.PackageAddOn.builder()
                .tourPackage(activePackage).vehicle(com.tourlk.entity.Vehicle.builder().id(8L).build()).build();
        when(addOnRepository.findByTourPackageId(100L)).thenReturn(List.of(roomAddOn, vehicleAddOn));
    }

    private BookingRequestDto requestWithAddOns(LocalDate travelDate) {
        BookingRequestDto request = new BookingRequestDto(100L, travelDate, 2, null);
        request.setAddOnRooms(List.of(new com.tourlk.dto.AddOnRoomSelectionDto(7L, 2)));
        request.setAddOnVehicleIds(List.of(8L));
        return request;
    }

    private com.tourlk.dto.RoomReservationResponseDto reservationPricedAt(String pricePerNight) {
        return com.tourlk.dto.RoomReservationResponseDto.builder()
                .room(com.tourlk.dto.RoomSummaryDto.builder().pricePerNight(new BigDecimal(pricePerNight)).build())
                .build();
    }

    @Test
    void createBooking_withAddOns_derivesDatesAndFreezesTheBreakdownTotal() {
        activePackage.setDurationDays(4);
        LocalDate travel = LocalDate.now().plusDays(30);
        stubBookingSave();
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        attachAddOns();
        // check-in = travel date, check-out = travel + (4 - 1) nights, vehicle held travel .. travel + 3
        when(roomReservationService.createLinkedReservation(any(Booking.class), eq(7L), eq(2), eq(travel),
                eq(travel.plusDays(3)))).thenReturn(reservationPricedAt("80.00"));
        when(vehicleHireService.createLinkedHire(any(Booking.class), eq(8L), eq(travel), eq(travel.plusDays(3)),
                any())).thenReturn(com.tourlk.dto.VehicleHireResponseDto.builder()
                .totalPrice(new BigDecimal("400.00")).build());

        BookingResponseDto result = bookingService.createBooking(requestWithAddOns(travel), tourist);

        assertThat(result.getPackageSubtotal()).isEqualByComparingTo("240.00");    // 120 x 2 travelers
        assertThat(result.getRoomsSubtotal()).isEqualByComparingTo("480.00");      // 80 x 3 nights x 2 rooms
        assertThat(result.getVehiclesSubtotal()).isEqualByComparingTo("400.00");
        assertThat(result.getTotalPrice()).isEqualByComparingTo("1120.00");
    }

    @Test
    void createBooking_oneDayPackage_stillReservesRoomsForAtLeastOneNight() {
        activePackage.setDurationDays(1);
        LocalDate travel = LocalDate.now().plusDays(30);
        stubBookingSave();
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        attachAddOns();
        when(roomReservationService.createLinkedReservation(any(), any(), anyInt(), any(), any()))
                .thenReturn(reservationPricedAt("10.00"));
        BookingRequestDto request = new BookingRequestDto(100L, travel, 2, null);
        request.setAddOnRooms(List.of(new com.tourlk.dto.AddOnRoomSelectionDto(7L, 1)));

        bookingService.createBooking(request, tourist);

        verify(roomReservationService).createLinkedReservation(any(), eq(7L), eq(1), eq(travel), eq(travel.plusDays(1)));
    }

    @Test
    void createBooking_whenAnAddOnIsUnavailable_propagatesSoTheWholeBookingRollsBack() {
        activePackage.setDurationDays(4);
        stubBookingSave();
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        attachAddOns();
        when(roomReservationService.createLinkedReservation(any(), any(), anyInt(), any(), any()))
                .thenReturn(reservationPricedAt("10.00"));
        when(vehicleHireService.createLinkedHire(any(), any(), any(), any(), any()))
                .thenThrow(new com.tourlk.exception.VehicleUnavailableException("This vehicle is already booked."));

        assertThatThrownBy(() -> bookingService.createBooking(requestWithAddOns(LocalDate.now().plusDays(30)), tourist))
                .isInstanceOf(com.tourlk.exception.VehicleUnavailableException.class)
                .hasMessage("This vehicle is already booked.");
    }

    @Test
    void createBooking_addOnNotAttachedToThePackage_isRejectedBeforeAnythingIsSaved() {
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(0);
        when(addOnRepository.findByTourPackageId(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.createBooking(requestWithAddOns(LocalDate.now().plusDays(30)), tourist))
                .isInstanceOf(BadRequestException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmBookingAfterPayment_confirmsLinkedReservationsAndHires() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));
        when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activePackage));
        when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(any(), any(), any())).thenReturn(0);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.confirmBookingAfterPayment(5L);

        verify(roomReservationService).confirmLinkedAfterPayment(5L);
        verify(vehicleHireService).confirmLinkedAfterPayment(5L);
    }

    @Test
    void confirmBookingAfterPayment_whenGuideAlreadyConfirmed_stillConfirmsLinkedItems() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));

        bookingService.confirmBookingAfterPayment(5L);

        verify(roomReservationService).confirmLinkedAfterPayment(5L);
        verify(vehicleHireService).confirmLinkedAfterPayment(5L);
    }

    @Test
    void cancelBooking_cancelsLinkedReservationsAndHires() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(5L, tourist);

        verify(roomReservationService).cancelLinkedToBooking(5L);
        verify(vehicleHireService).cancelLinkedToBooking(5L);
    }

    @Test
    void rejectBooking_cancelsLinkedReservationsAndHires() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.PENDING, tourist)));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.rejectBooking(5L, "Fully booked", guide);

        verify(roomReservationService).cancelLinkedToBooking(5L);
        verify(vehicleHireService).cancelLinkedToBooking(5L);
    }

    @Test
    void expireUnpaidBooking_cancelsPendingBookingAndItsAddOns() {
        Booking pending = booking(BookingStatus.PENDING, tourist);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(bookingService.expireUnpaidBooking(5L)).isTrue();

        assertThat(pending.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(roomReservationService).cancelLinkedToBooking(5L);
        verify(vehicleHireService).cancelLinkedToBooking(5L);
    }

}
