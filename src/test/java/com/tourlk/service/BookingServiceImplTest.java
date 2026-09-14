package com.tourlk.service;

import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.Destination;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.CapacityExceededException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User tourist;
    private User otherTourist;
    private User admin;
    private TourPackage activePackage;

    @BeforeEach
    void setUp() {
        tourist = User.builder().id(1L).name("Tess").email("tess@example.com").role(Role.TOURIST).build();
        otherTourist = User.builder().id(2L).name("Otto").email("otto@example.com").role(Role.TOURIST).build();
        admin = User.builder().id(9L).name("Amy Admin").email("admin@example.com").role(Role.ADMIN).build();
        activePackage = TourPackage.builder()
                .id(100L).title("Hill Country")
                .destination(Destination.builder()
                        .id(1L).name("Kandy").region("Central Province").status(DestinationStatus.ACTIVE).build())
                .price(new BigDecimal("120.00")).maxCapacity(10).status(PackageStatus.ACTIVE)
                .build();
    }

    private Booking booking(BookingStatus status, User owner) {
        return Booking.builder()
                .id(5L).tourist(owner).tourPackage(activePackage)
                .travelDate(LocalDate.now().plusDays(30)).numberOfTravelers(2)
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

        BookingResponseDto result = bookingService.confirmBooking(5L);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmBooking_notPending_throwsInvalidStatusTransition() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, tourist)));

        assertThatThrownBy(() -> bookingService.confirmBooking(5L))
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
}
