package com.tourlk.exception;

/**
 * Thrown when the reviewableId a client sent doesn't match the
 * TourPackage/Accommodation/Vehicle that the referenced source
 * Booking/RoomReservation/VehicleHire was actually for — prevents
 * reviewing item B using a completed booking for item A. See
 * {@code ReviewServiceImpl#createReview}.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 400.
 */
public class ReviewableMismatchException extends RuntimeException {

    public ReviewableMismatchException(String message) {
        super(message);
    }

}
