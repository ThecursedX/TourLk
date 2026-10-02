package com.tourlk.service;

import com.tourlk.dto.PaymentMethodResponseDto;
import com.tourlk.dto.SavePaymentMethodRequestDto;
import com.tourlk.dto.SetupIntentResponseDto;
import com.tourlk.entity.User;

import java.util.List;

public interface PaymentMethodService {

    /** Creates (lazily) a Stripe Customer for the user and a SetupIntent so Stripe.js can collect a card. */
    SetupIntentResponseDto createSetupIntent(User currentUser);

    /** Persists the card Stripe.js just attached to the user's Stripe Customer via the SetupIntent above. */
    PaymentMethodResponseDto savePaymentMethod(User currentUser, SavePaymentMethodRequestDto request);

    List<PaymentMethodResponseDto> getMyPaymentMethods(User currentUser);

    /** @throws com.tourlk.exception.ResourceNotFoundException if the method doesn't belong to currentUser */
    PaymentMethodResponseDto setDefault(Long id, User currentUser);

    /** @throws com.tourlk.exception.ResourceNotFoundException if the method doesn't belong to currentUser */
    void deletePaymentMethod(Long id, User currentUser);

}
