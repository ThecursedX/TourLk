package com.tourlk.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.tourlk.dto.PaymentMethodResponseDto;
import com.tourlk.dto.SavePaymentMethodRequestDto;
import com.tourlk.dto.SetupIntentResponseDto;
import com.tourlk.entity.SavedPaymentMethod;
import com.tourlk.entity.User;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.PaymentGatewayException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.SavedPaymentMethodRepository;
import com.tourlk.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Never touches full card numbers — Stripe.js collects and tokenizes the
 * card in the browser, and we only ever persist the resulting Stripe
 * PaymentMethod id plus display-only details (brand/last4/expiry), the
 * same principle {@code Payment} follows for one-off charges.
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SetupIntentResponseDto createSetupIntent(User currentUser) {
        String customerId = getOrCreateStripeCustomer(currentUser);
        try {
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .addPaymentMethodType("card")
                    .build();
            SetupIntent setupIntent = SetupIntent.create(params);
            return SetupIntentResponseDto.builder().clientSecret(setupIntent.getClientSecret()).build();
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not start saving a card: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public PaymentMethodResponseDto savePaymentMethod(User currentUser, SavePaymentMethodRequestDto request) {
        User user = getUser(currentUser.getId());

        PaymentMethod stripePaymentMethod;
        try {
            stripePaymentMethod = PaymentMethod.retrieve(request.getPaymentMethodId());
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not verify this card with Stripe: " + e.getMessage(), e);
        }

        if (stripePaymentMethod.getCustomer() == null
                || !stripePaymentMethod.getCustomer().equals(user.getStripeCustomerId())) {
            throw new BadRequestException("This payment method is not associated with your account");
        }
        if (stripePaymentMethod.getCard() == null) {
            throw new BadRequestException("Only card payment methods can be saved");
        }
        if (savedPaymentMethodRepository.findByStripePaymentMethodId(stripePaymentMethod.getId()).isPresent()) {
            throw new BadRequestException("This card has already been saved");
        }

        boolean isFirstCard = savedPaymentMethodRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty();

        SavedPaymentMethod saved = SavedPaymentMethod.builder()
                .user(user)
                .stripePaymentMethodId(stripePaymentMethod.getId())
                .brand(stripePaymentMethod.getCard().getBrand())
                .last4(stripePaymentMethod.getCard().getLast4())
                .expMonth(stripePaymentMethod.getCard().getExpMonth() == null
                        ? null : stripePaymentMethod.getCard().getExpMonth().intValue())
                .expYear(stripePaymentMethod.getCard().getExpYear() == null
                        ? null : stripePaymentMethod.getCard().getExpYear().intValue())
                .defaultCard(isFirstCard)
                .build();

        return toResponse(savedPaymentMethodRepository.save(saved));
    }

    @Override
    public List<PaymentMethodResponseDto> getMyPaymentMethods(User currentUser) {
        return savedPaymentMethodRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PaymentMethodResponseDto setDefault(Long id, User currentUser) {
        SavedPaymentMethod target = getOwned(id, currentUser);

        savedPaymentMethodRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).forEach(pm -> {
            if (pm.isDefaultCard() && !pm.getId().equals(id)) {
                pm.setDefaultCard(false);
                savedPaymentMethodRepository.save(pm);
            }
        });

        target.setDefaultCard(true);
        return toResponse(savedPaymentMethodRepository.save(target));
    }

    @Override
    @Transactional
    public void deletePaymentMethod(Long id, User currentUser) {
        SavedPaymentMethod target = getOwned(id, currentUser);
        try {
            PaymentMethod.retrieve(target.getStripePaymentMethodId()).detach();
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not remove this card: " + e.getMessage(), e);
        }
        savedPaymentMethodRepository.delete(target);
    }

    private String getOrCreateStripeCustomer(User currentUser) {
        User user = getUser(currentUser.getId());
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getName())
                    .build();
            Customer customer = Customer.create(params);
            user.setStripeCustomerId(customer.getId());
            userRepository.save(user);
            return customer.getId();
        } catch (StripeException e) {
            throw new PaymentGatewayException("Could not set up saved cards: " + e.getMessage(), e);
        }
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private SavedPaymentMethod getOwned(Long id, User currentUser) {
        SavedPaymentMethod pm = savedPaymentMethodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found with id: " + id));
        if (!pm.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Payment method not found with id: " + id);
        }
        return pm;
    }

    private PaymentMethodResponseDto toResponse(SavedPaymentMethod pm) {
        return PaymentMethodResponseDto.builder()
                .id(pm.getId())
                .brand(pm.getBrand())
                .last4(pm.getLast4())
                .expMonth(pm.getExpMonth())
                .expYear(pm.getExpYear())
                .defaultCard(pm.isDefaultCard())
                .build();
    }

}
