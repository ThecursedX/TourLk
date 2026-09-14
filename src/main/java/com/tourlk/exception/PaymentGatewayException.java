package com.tourlk.exception;

/**
 * Thrown when a call to Stripe itself fails (network error, declined
 * request, etc — wraps {@code com.stripe.exception.StripeException}, a
 * checked exception, into something GlobalExceptionHandler can catch).
 * Handled by {@link GlobalExceptionHandler} and mapped to HTTP 502.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

}
