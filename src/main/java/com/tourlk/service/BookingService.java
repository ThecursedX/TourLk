package com.tourlk.service;

import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.User;

import java.util.List;

public interface BookingService {

    BookingResponseDto createBooking(BookingRequestDto request, User currentUser);

    BookingResponseDto confirmBooking(Long id);

    BookingResponseDto requestReschedule(Long id, RescheduleRequestDto request, User currentUser);

    BookingResponseDto approveReschedule(Long id);

    BookingResponseDto rejectReschedule(Long id);

    BookingResponseDto cancelBooking(Long id, User currentUser);

    BookingResponseDto completeBooking(Long id);

    BookingResponseDto getBookingById(Long id, User currentUser);

    List<BookingResponseDto> getBookingsByTourist(Long touristId);

    List<BookingResponseDto> getBookingsByPackage(Long packageId);

}
