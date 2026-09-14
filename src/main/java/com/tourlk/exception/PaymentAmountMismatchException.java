package com.tourlk.exception;

/**
 * Thrown when the amount a client sent with a payment request doesn't
 * match the real, server-computed price of the Booking/RoomReservation
 * being paid for. Never trust the client-sent amount for the actual
 * charge — see {@code PaymentServiceImpl#createPaymentIntent}.
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 400.
 */
public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(String message) {
        super(message);
    }

}
