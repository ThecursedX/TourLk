package com.tourlk.service;

import com.tourlk.dto.PackageDepartureRequestDto;
import com.tourlk.dto.PackageDepartureResponseDto;
import com.tourlk.entity.PackageDeparture;
import com.tourlk.entity.TourPackage;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.DepartureHasBookingsException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.BookingRepository;
import com.tourlk.repo.PackageDepartureRepository;
import com.tourlk.repo.TourPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PackageDepartureServiceImpl}: add (seat default,
 * duplicates, ownership, archived), delete (active-booking guard,
 * ownership) and the public upcoming list with seatsLeft.
 */
@ExtendWith(MockitoExtension.class)
class PackageDepartureServiceImplTest {

    @Mock
    private PackageDepartureRepository departureRepository;
    @Mock
    private TourPackageRepository tourPackageRepository;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private PackageDepartureServiceImpl service;

    private User owner;
    private User stranger;
    private User admin;
    private TourPackage tourPackage;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).name("Gina Guide").role(Role.GUIDE).build();
        stranger = User.builder().id(2L).name("Other Guide").role(Role.GUIDE).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
        tourPackage = TourPackage.builder()
                .id(100L).title("Hill Country").maxCapacity(12).status(PackageStatus.ACTIVE).createdBy(owner)
                .build();
        date = LocalDate.now().plusDays(30);
    }

    private PackageDeparture departure(int seatsTotal) {
        return PackageDeparture.builder()
                .id(7L).tourPackage(tourPackage).departureDate(date).seatsTotal(seatsTotal)
                .build();
    }

    @Nested
    class AddDeparture {

        @BeforeEach
        void lockPackage() {
            when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tourPackage));
        }

        @Test
        void noSeatsGiven_defaultsToPackageMaxCapacity() {
            when(departureRepository.save(any(PackageDeparture.class))).thenAnswer(inv -> inv.getArgument(0));

            PackageDepartureResponseDto result =
                    service.addDeparture(100L, new PackageDepartureRequestDto(date, null), owner);

            ArgumentCaptor<PackageDeparture> saved = ArgumentCaptor.forClass(PackageDeparture.class);
            verify(departureRepository).save(saved.capture());
            assertThat(saved.getValue().getSeatsTotal()).isEqualTo(12);
            assertThat(saved.getValue().getTourPackage()).isSameAs(tourPackage);
            assertThat(result.getSeatsTotal()).isEqualTo(12);
            assertThat(result.getSeatsLeft()).isEqualTo(12);
            assertThat(result.getDepartureDate()).isEqualTo(date);
        }

        @Test
        void explicitSeats_areUsed() {
            when(departureRepository.save(any(PackageDeparture.class))).thenAnswer(inv -> inv.getArgument(0));

            PackageDepartureResponseDto result =
                    service.addDeparture(100L, new PackageDepartureRequestDto(date, 6), admin);

            assertThat(result.getSeatsTotal()).isEqualTo(6);
        }

        @Test
        void existingBookingsOnThatDate_reduceSeatsLeft() {
            when(departureRepository.save(any(PackageDeparture.class))).thenAnswer(inv -> inv.getArgument(0));
            when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(100L, date, BookingStatus.CAPACITY_HOLDING))
                    .thenReturn(4);

            PackageDepartureResponseDto result =
                    service.addDeparture(100L, new PackageDepartureRequestDto(date, 6), owner);

            assertThat(result.getSeatsLeft()).isEqualTo(2);
        }

        @Test
        void duplicateDate_throwsBadRequest() {
            when(departureRepository.existsByTourPackageIdAndDepartureDate(100L, date)).thenReturn(true);

            assertThatThrownBy(() -> service.addDeparture(100L, new PackageDepartureRequestDto(date, null), owner))
                    .isInstanceOf(BadRequestException.class);
            verify(departureRepository, never()).save(any());
        }

        @Test
        void byNonOwner_throwsAccessDenied() {
            assertThatThrownBy(() -> service.addDeparture(100L, new PackageDepartureRequestDto(date, null), stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(departureRepository, never()).save(any());
        }

        @Test
        void archivedPackage_throwsInvalidStatusTransition() {
            tourPackage.setStatus(PackageStatus.ARCHIVED);

            assertThatThrownBy(() -> service.addDeparture(100L, new PackageDepartureRequestDto(date, null), owner))
                    .isInstanceOf(InvalidStatusTransitionException.class);
            verify(departureRepository, never()).save(any());
        }
    }

    @Test
    void addDeparture_packageNotFound_throwsResourceNotFound() {
        when(tourPackageRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addDeparture(404L, new PackageDepartureRequestDto(date, null), owner))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Nested
    class DeleteDeparture {

        @BeforeEach
        void lockPackage() {
            when(tourPackageRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(tourPackage));
        }

        @Test
        void noActiveBookings_deletes() {
            PackageDeparture departure = departure(12);
            when(departureRepository.findByIdAndTourPackageId(7L, 100L)).thenReturn(Optional.of(departure));
            when(bookingRepository.countActiveBookingsOnDate(100L, date, BookingStatus.TERMINAL)).thenReturn(0L);

            service.deleteDeparture(100L, 7L, owner);

            verify(departureRepository).delete(departure);
        }

        @Test
        void withActiveBookings_throwsAndKeepsDeparture() {
            when(departureRepository.findByIdAndTourPackageId(7L, 100L)).thenReturn(Optional.of(departure(12)));
            when(bookingRepository.countActiveBookingsOnDate(eq(100L), eq(date), any())).thenReturn(2L);

            assertThatThrownBy(() -> service.deleteDeparture(100L, 7L, admin))
                    .isInstanceOf(DepartureHasBookingsException.class)
                    .hasMessageContaining("2 active booking");
            verify(departureRepository, never()).delete(any());
        }

        @Test
        void departureOfAnotherPackage_throwsResourceNotFound() {
            when(departureRepository.findByIdAndTourPackageId(7L, 100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteDeparture(100L, 7L, owner))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(departureRepository, never()).delete(any());
        }

        @Test
        void byNonOwner_throwsAccessDenied() {
            assertThatThrownBy(() -> service.deleteDeparture(100L, 7L, stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(departureRepository, never()).delete(any());
        }
    }

    @Nested
    class GetUpcomingDepartures {

        @Test
        void listsFutureDeparturesWithSeatsLeft_clampedAtZero() {
            PackageDeparture open = departure(10);
            PackageDeparture overbooked = PackageDeparture.builder()
                    .id(8L).tourPackage(tourPackage).departureDate(date.plusDays(7)).seatsTotal(3).build();
            when(tourPackageRepository.existsById(100L)).thenReturn(true);
            when(departureRepository.findByTourPackageIdAndDepartureDateAfterOrderByDepartureDateAsc(
                    100L, LocalDate.now())).thenReturn(List.of(open, overbooked));
            when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(100L, date, BookingStatus.CAPACITY_HOLDING))
                    .thenReturn(4);
            // Seats reduced below what's already booked (e.g. legacy bookings) never go negative.
            when(bookingRepository.sumTravelersByPackageAndDateAndStatusIn(
                    100L, date.plusDays(7), BookingStatus.CAPACITY_HOLDING)).thenReturn(5);

            List<PackageDepartureResponseDto> result = service.getUpcomingDepartures(100L);

            assertThat(result).extracting(PackageDepartureResponseDto::getSeatsLeft).containsExactly(6, 0);
            assertThat(result).extracting(PackageDepartureResponseDto::getId).containsExactly(7L, 8L);
        }

        @Test
        void packageNotFound_throwsResourceNotFound() {
            when(tourPackageRepository.existsById(404L)).thenReturn(false);

            assertThatThrownBy(() -> service.getUpcomingDepartures(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
