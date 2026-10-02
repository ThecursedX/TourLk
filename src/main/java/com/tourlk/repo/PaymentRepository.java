package com.tourlk.repo;

import com.tourlk.entity.Payment;
import com.tourlk.enums.PayableType;
import com.tourlk.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByPayerId(Long payerId);

    long countByPayerId(Long payerId);

    List<Payment> findByPayableTypeAndPayableId(PayableType payableType, Long payableId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status IN :statuses")
    BigDecimal sumAmountByStatusIn(@Param("statuses") Collection<PaymentStatus> statuses);

    @Query("SELECT COALESCE(SUM(p.refundAmount), 0) FROM Payment p WHERE p.status = :status")
    BigDecimal sumRefundAmountByStatus(@Param("status") PaymentStatus status);

    long countByStatus(PaymentStatus status);

}
