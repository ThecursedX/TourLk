package com.tourlk.controller;

import com.tourlk.dto.PaymentMethodResponseDto;
import com.tourlk.dto.SavePaymentMethodRequestDto;
import com.tourlk.dto.SetupIntentResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.PaymentMethodService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Payment Methods", description = "Saved cards for checkout, backed by Stripe SetupIntents/PaymentMethods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final UserService userService;

    @PostMapping("/setup-intent")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<SetupIntentResponseDto> createSetupIntent(Authentication authentication) {
        return ResponseEntity.ok(paymentMethodService.createSetupIntent(currentUser(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<PaymentMethodResponseDto> save(@Valid @RequestBody SavePaymentMethodRequestDto request,
                                                          Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentMethodService.savePaymentMethod(currentUser(authentication), request));
    }

    @GetMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<List<PaymentMethodResponseDto>> mine(Authentication authentication) {
        return ResponseEntity.ok(paymentMethodService.getMyPaymentMethods(currentUser(authentication)));
    }

    @PutMapping("/{id}/default")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<PaymentMethodResponseDto> setDefault(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(paymentMethodService.setDefault(id, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        paymentMethodService.deletePaymentMethod(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
