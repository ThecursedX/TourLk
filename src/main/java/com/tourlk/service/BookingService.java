package com.tourlk.service;

import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.User;

import java.util.List;

public interface BookingService {

    BookingResponseDto createBooking(BookingRequestDto request, User currentUser);

    BookingResponseDto confirmBooking(Long id);

    BookingResponseDto requestReschedule(Long id, RescheduleRequestDto request, User currentUser);

    BookingResponseDto approveReschedule(Long id);

    BookingResponseDto rejectReschedule(Long id);

    /**
     * Cancels an active booking. If it has a SUCCEEDED payment, the
     * configured cancellation policy (see {@link CancellationPolicy})
     * refunds whatever percentage applies for how close travelDate is; a
     * payment that never completed (still PENDING) is marked CANCELLED.
     */
    BookingResponseDto cancelBooking(Long id, User currentUser);

    /** What cancelling this booking today would refund, without cancelling it. */
    CancellationPreviewResponseDto getCancellationPreview(Long id, User currentUser);

    /**
     * Turns down a PENDING booking with a reason — the package owner
     * (GUIDE) or an ADMIN.
     *
     * @throws com.tourlk.exception.BadRequestException if the reason is blank
     * @throws com.tourlk.exception.InvalidStatusTransitionException if the booking isn't PENDING
     */
    BookingResponseDto rejectBooking(Long id, String reason, User currentUser);

    BookingResponseDto completeBooking(Long id);

    BookingResponseDto getBookingById(Long id, User currentUser);

    List<BookingResponseDto> getBookingsByTourist(Long touristId);

    /** ADMIN sees any package; a GUIDE only their own. */
    List<BookingResponseDto> getBookingsByPackage(Long packageId, User currentUser);

}
