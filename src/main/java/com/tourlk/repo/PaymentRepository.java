package com.tourlk.repo;

import com.tourlk.entity.Payment;
import com.tourlk.enums.PayableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByPayerId(Long payerId);

    List<Payment> findByPayableTypeAndPayableId(PayableType payableType, Long payableId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

}
