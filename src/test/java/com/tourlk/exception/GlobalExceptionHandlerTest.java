package com.tourlk.exception;

import com.tourlk.dto.LoginRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that {@link GlobalExceptionHandler} maps each application exception
 * to the right HTTP status and the standard error-body shape. Runs against a
 * standalone MockMvc (no security filters) so handlers like the
 * AccessDeniedException / BadCredentialsException ones are actually reached.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class NotFoundAndBadRequest {

        @Test
        void resourceNotFound_maps404() throws Exception {
            mvc.perform(get("/err/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("boom"))
                    .andExpect(jsonPath("$.path").value("/err/not-found"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void badRequest_maps400() throws Exception {
            mvc.perform(get("/err/bad-request"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("boom"));
        }

        @Test
        void invalidDateRange_maps400() throws Exception {
            mvc.perform(get("/err/invalid-date")).andExpect(status().isBadRequest());
        }

        @Test
        void paymentAmountMismatch_maps400() throws Exception {
            mvc.perform(get("/err/amount-mismatch")).andExpect(status().isBadRequest());
        }

        @Test
        void paymentRequired_maps400() throws Exception {
            mvc.perform(get("/err/payment-required")).andExpect(status().isBadRequest());
        }

        @Test
        void reviewNotEligible_maps400() throws Exception {
            mvc.perform(get("/err/review-not-eligible")).andExpect(status().isBadRequest());
        }

        @Test
        void reviewableMismatch_maps400() throws Exception {
            mvc.perform(get("/err/reviewable-mismatch")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Conflict {

        @Test
        void invalidStatusTransition_maps409() throws Exception {
            mvc.perform(get("/err/invalid-status")).andExpect(status().isConflict());
        }

        @Test
        void capacityExceeded_maps409() throws Exception {
            mvc.perform(get("/err/capacity")).andExpect(status().isConflict());
        }

        @Test
        void roomUnavailable_maps409() throws Exception {
            mvc.perform(get("/err/room-unavailable")).andExpect(status().isConflict());
        }

        @Test
        void vehicleUnavailable_maps409() throws Exception {
            mvc.perform(get("/err/vehicle-unavailable")).andExpect(status().isConflict());
        }

        @Test
        void duplicateReview_maps409() throws Exception {
            mvc.perform(get("/err/duplicate-review")).andExpect(status().isConflict());
        }

        @Test
        void ticketClosed_maps409() throws Exception {
            mvc.perform(get("/err/ticket-closed")).andExpect(status().isConflict());
        }

        @Test
        void duplicateDestination_maps409() throws Exception {
            mvc.perform(get("/err/duplicate-destination")).andExpect(status().isConflict());
        }

        @Test
        void destinationInactive_maps409() throws Exception {
            mvc.perform(get("/err/destination-inactive")).andExpect(status().isConflict());
        }

        @Test
        void pessimisticLockFailure_maps409WithFriendlyMessage() throws Exception {
            mvc.perform(get("/err/lock"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "This resource is busy processing another request. Please try again."));
        }
    }

    @Nested
    class AuthAndAccess {

        @Test
        void badCredentials_maps401WithGenericMessage() throws Exception {
            mvc.perform(get("/err/bad-credentials"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        @Test
        void accessDenied_maps403WithGenericMessage() throws Exception {
            mvc.perform(get("/err/access-denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
        }

        @Test
        void ticketAccessDenied_maps403WithItsOwnMessage() throws Exception {
            mvc.perform(get("/err/ticket-access"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("boom"));
        }
    }

    @Nested
    class GatewayAndGeneric {

        @Test
        void paymentGateway_maps502() throws Exception {
            mvc.perform(get("/err/gateway")).andExpect(status().isBadGateway());
        }

        @Test
        void unmappedException_maps500WithGenericMessage() throws Exception {
            mvc.perform(get("/err/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }
    }

    @Nested
    class Validation {

        @Test
        void methodArgumentNotValid_maps400WithFieldErrors() throws Exception {
            mvc.perform(post("/err/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.password").exists());
        }
    }

    // ------------------------------------------------------------------

    @RestController
    static class ThrowingController {

        @GetMapping("/err/{kind}")
        void raise(@PathVariable String kind) {
            switch (kind) {
                case "not-found" -> throw new ResourceNotFoundException("boom");
                case "bad-request" -> throw new BadRequestException("boom");
                case "invalid-status" -> throw new InvalidStatusTransitionException("boom");
                case "capacity" -> throw new CapacityExceededException("boom");
                case "room-unavailable" -> throw new RoomUnavailableException("boom");
                case "vehicle-unavailable" -> throw new VehicleUnavailableException("boom");
                case "invalid-date" -> throw new InvalidDateRangeException("boom");
                case "amount-mismatch" -> throw new PaymentAmountMismatchException("boom");
                case "payment-required" -> throw new PaymentRequiredException("boom");
                case "gateway" -> throw new PaymentGatewayException("boom", new RuntimeException("stripe"));
                case "review-not-eligible" -> throw new ReviewNotEligibleException("boom");
                case "duplicate-review" -> throw new DuplicateReviewException("boom");
                case "reviewable-mismatch" -> throw new ReviewableMismatchException("boom");
                case "ticket-access" -> throw new TicketAccessDeniedException("boom");
                case "ticket-closed" -> throw new TicketClosedException("boom");
                case "duplicate-destination" -> throw new DuplicateDestinationException("boom");
                case "destination-inactive" -> throw new DestinationInactiveException("boom");
                case "bad-credentials" -> throw new BadCredentialsException("raw message");
                case "access-denied" -> throw new AccessDeniedException("raw message");
                case "lock" -> throw new PessimisticLockingFailureException("locked");
                default -> throw new IllegalStateException("boom");
            }
        }

        @PostMapping("/err/validate")
        void validate(@Valid @RequestBody LoginRequestDto body) {
            // never reached — validation fails first
        }
    }
}
