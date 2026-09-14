package com.tourlk.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.tourlk.dto.InvoiceResponseDto;
import com.tourlk.dto.PaymentIntentResponseDto;
import com.tourlk.dto.PaymentRequestDto;
import com.tourlk.dto.PaymentResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.PaymentService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Consumer;

/**
 * Payment creation and the Stripe webhook. POST /webhook is listed as
 * public in {@code SecurityConfig} (Stripe can't send a JWT) — its only
 * protection is the signature check below, so that check is not
 * optional: never call into {@code PaymentService} from this endpoint
 * without it passing first.
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Pay for a booking/reservation via Stripe, and view payment history/invoices")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;
    private final String webhookSecret;

    public PaymentController(PaymentService paymentService,
                              UserService userService,
                              @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.paymentService = paymentService;
        this.userService = userService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/intent")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<PaymentIntentResponseDto> createIntent(@Valid @RequestBody PaymentRequestDto request,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(paymentService.createPaymentIntent(request, currentUser(authentication)));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                         @RequestHeader("Stripe-Signature") String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected a Stripe webhook call with an invalid signature");
            return ResponseEntity.badRequest().build();
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handleIntentEvent(event, paymentService::handlePaymentSucceeded);
            case "payment_intent.payment_failed" -> handleIntentEvent(event, paymentService::handlePaymentFailed);
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponseDto> refund(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PaymentResponseDto>> all() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<List<PaymentResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(paymentService.getPaymentsByUser(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPaymentById(id, currentUser(authentication)));
    }

    @GetMapping("/{id}/invoice")
    public ResponseEntity<InvoiceResponseDto> getInvoice(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getInvoiceByPaymentId(id, currentUser(authentication)));
    }

    private void handleIntentEvent(Event event, Consumer<String> handler) {
        event.getDataObjectDeserializer().getObject()
                .map(stripeObject -> (PaymentIntent) stripeObject)
                .ifPresent(intent -> handler.accept(intent.getId()));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
