package com.tourlk.repo;

import com.tourlk.entity.SavedPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, Long> {

    List<SavedPaymentMethod> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedPaymentMethod> findByStripePaymentMethodId(String stripePaymentMethodId);

}
