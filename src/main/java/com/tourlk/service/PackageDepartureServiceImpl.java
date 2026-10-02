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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Add/remove takes the same pessimistic package lock as
 * {@code BookingServiceImpl}, so a departure can't be removed between a
 * concurrent booking's "is this a departure date?" check and its insert,
 * and the first departure being added can't race a free-date booking.
 */
@Service
@RequiredArgsConstructor
public class PackageDepartureServiceImpl implements PackageDepartureService {

    private final PackageDepartureRepository departureRepository;
    private final TourPackageRepository tourPackageRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public PackageDepartureResponseDto addDeparture(Long packageId, PackageDepartureRequestDto request,
                                                    User currentUser) {
        TourPackage tourPackage = getLockedPackage(packageId);
        assertCanManage(tourPackage, currentUser);

        if (tourPackage.getStatus() == PackageStatus.ARCHIVED) {
            throw new InvalidStatusTransitionException("Departures cannot be added to an archived package");
        }
        if (departureRepository.existsByTourPackageIdAndDepartureDate(packageId, request.getDepartureDate())) {
            throw new BadRequestException("This package already has a departure on " + request.getDepartureDate());
        }

        int seatsTotal = request.getSeatsTotal() != null ? request.getSeatsTotal() : tourPackage.getMaxCapacity();

        PackageDeparture departure = departureRepository.save(PackageDeparture.builder()
                .tourPackage(tourPackage)
                .departureDate(request.getDepartureDate())
                .seatsTotal(seatsTotal)
                .build());

        return toResponse(departure, packageId);
    }

    @Override
    @Transactional
    public void deleteDeparture(Long packageId, Long departureId, User currentUser) {
        TourPackage tourPackage = getLockedPackage(packageId);
        assertCanManage(tourPackage, currentUser);

        PackageDeparture departure = departureRepository.findByIdAndTourPackageId(departureId, packageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Departure not found with id: " + departureId + " on package " + packageId));

        long activeBookings = bookingRepository.countActiveBookingsOnDate(
                packageId, departure.getDepartureDate(), BookingStatus.TERMINAL);
        if (activeBookings > 0) {
            throw new DepartureHasBookingsException("The departure on " + departure.getDepartureDate() + " has "
                    + activeBookings + " active booking(s); cancel or move them before removing it");
        }

        departureRepository.delete(departure);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackageDepartureResponseDto> getUpcomingDepartures(Long packageId) {
        if (!tourPackageRepository.existsById(packageId)) {
            throw new ResourceNotFoundException("Tour package not found with id: " + packageId);
        }

        return departureRepository
                .findByTourPackageIdAndDepartureDateAfterOrderByDepartureDateAsc(packageId, LocalDate.now())
                .stream()
                .map(departure -> toResponse(departure, packageId))
                .toList();
    }

    private TourPackage getLockedPackage(Long packageId) {
        return tourPackageRepository.findByIdForUpdate(packageId)
                .orElseThrow(() -> new ResourceNotFoundException("Tour package not found with id: " + packageId));
    }

    private void assertCanManage(TourPackage tourPackage, User currentUser) {
        boolean isOwner = tourPackage.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to manage this package");
        }
    }

    private PackageDepartureResponseDto toResponse(PackageDeparture departure, Long packageId) {
        int booked = bookingRepository.sumTravelersByPackageAndDateAndStatusIn(
                packageId, departure.getDepartureDate(), BookingStatus.CAPACITY_HOLDING);

        return PackageDepartureResponseDto.builder()
                .id(departure.getId())
                .departureDate(departure.getDepartureDate())
                .seatsTotal(departure.getSeatsTotal())
                .seatsLeft(Math.max(0, departure.getSeatsTotal() - booked))
                .build();
    }

}
