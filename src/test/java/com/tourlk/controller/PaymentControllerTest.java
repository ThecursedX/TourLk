package com.tourlk.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.tourlk.dto.PaymentIntentResponseDto;
import com.tourlk.dto.PaymentRequestDto;
import com.tourlk.dto.PaymentResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import com.tourlk.enums.Role;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.PaymentService;
import com.tourlk.service.UserService;
import com.tourlk.support.MethodSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link PaymentController}: Stripe webhook signature
 * verification and {@code @PreAuthorize} role enforcement. The service
 * layer and the Stripe SDK's static {@code Webhook.constructEvent} are
 * mocked. Security is a permissive test chain with method security on, so
 * only {@code @PreAuthorize} decides who gets in.
 */
@WebMvcTest(controllers = PaymentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
@TestPropertySource(properties = "stripe.webhook-secret=whsec_test_secret")
class PaymentControllerTest {

    private static final String VALID_INTENT_BODY = """
            {"payableType":"BOOKING","payableId":1,"amount":200.00}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PaymentService paymentService;
    @MockBean
    private UserService userService;

    // ------------------------------------------------------------------
    // POST /api/payments/intent  — @PreAuthorize("hasRole('TOURIST')")
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "tourist@example.com", roles = "TOURIST")
    void createIntent_asTourist_returnsClientSecret() throws Exception {
        when(userService.getByEmail("tourist@example.com"))
                .thenReturn(User.builder().id(1L).email("tourist@example.com").role(Role.TOURIST).build());
        when(paymentService.createPaymentIntent(any(PaymentRequestDto.class), any(User.class)))
                .thenReturn(PaymentIntentResponseDto.builder().clientSecret("cs_123").paymentId(9L).build());

        mvc.perform(post("/api/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INTENT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("cs_123"))
                .andExpect(jsonPath("$.paymentId").value(9));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void createIntent_asAdmin_isForbidden() throws Exception {
        mvc.perform(post("/api/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INTENT_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = "guide@example.com", roles = "GUIDE")
    void createIntent_asGuide_isForbidden() throws Exception {
        mvc.perform(post("/api/payments/intent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_INTENT_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    // ------------------------------------------------------------------
    // POST /api/payments/webhook  — signature verification, public
    // ------------------------------------------------------------------

    @Test
    void webhook_validSignature_succeededEvent_dispatchesToService() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_abc123");
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.<StripeObject>of(intent));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            mvc.perform(post("/api/payments/webhook")
                            .header("Stripe-Signature", "t=1,v1=validsig")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}"))
                    .andExpect(status().isOk());
        }

        verify(paymentService).handlePaymentSucceeded("pi_abc123");
    }

    @Test
    void webhook_validSignature_failedEvent_dispatchesToService() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.payment_failed");

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn("pi_failed");
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.<StripeObject>of(intent));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            mvc.perform(post("/api/payments/webhook")
                            .header("Stripe-Signature", "t=1,v1=validsig")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
        }

        verify(paymentService).handlePaymentFailed("pi_failed");
    }

    @Test
    void webhook_invalidSignature_returns400AndNeverReachesService() throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad signature", "t=1,v1=bad"));

            mvc.perform(post("/api/payments/webhook")
                            .header("Stripe-Signature", "t=1,v1=bad")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"evt_1\"}"))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(paymentService);
    }

    @Test
    void webhook_validSignature_unhandledEventType_returns200AndIgnores() throws Exception {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("customer.created");

        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            mvc.perform(post("/api/payments/webhook")
                            .header("Stripe-Signature", "t=1,v1=validsig")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"evt_1\"}"))
                    .andExpect(status().isOk());
        }

        verifyNoInteractions(paymentService);
    }

    // ------------------------------------------------------------------
    // PUT /api/payments/{id}/refund  — @PreAuthorize("hasRole('ADMIN')")
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void refund_asAdmin_returnsRefundedPayment() throws Exception {
        when(paymentService.refundPayment(5L)).thenReturn(PaymentResponseDto.builder()
                .id(5L)
                .status(PaymentStatus.REFUNDED)
                .payableType(PayableType.BOOKING)
                .payableId(10L)
                .amount(new BigDecimal("200.00"))
                .currency("usd")
                .build());

        mvc.perform(put("/api/payments/5/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @WithMockUser(username = "tourist@example.com", roles = "TOURIST")
    void refund_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/payments/5/refund"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    // ------------------------------------------------------------------
    // GET /api/payments  — @PreAuthorize("hasRole('ADMIN')")
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void listAllPayments_asAdmin_returnsList() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of(PaymentResponseDto.builder()
                .id(1L).status(PaymentStatus.SUCCEEDED).payableType(PayableType.BOOKING)
                .payableId(10L).amount(new BigDecimal("200.00")).currency("usd").build()));

        mvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @WithMockUser(username = "tourist@example.com", roles = "TOURIST")
    void listAllPayments_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/payments"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }
}
