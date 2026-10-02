package com.tourlk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A card saved for reuse at checkout, backed by a Stripe PaymentMethod
 * attached to the user's Stripe Customer. We never store full card
 * numbers — only the Stripe PaymentMethod id and the display details
 * (brand/last4/expiry) Stripe returns for it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "saved_payment_methods")
public class SavedPaymentMethod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_payment_method_id", nullable = false, unique = true, length = 255)
    private String stripePaymentMethodId;

    @Column(length = 30)
    private String brand;

    @Column(length = 4)
    private String last4;

    @Column(name = "exp_month")
    private Integer expMonth;

    @Column(name = "exp_year")
    private Integer expYear;

    @Column(name = "default_card", nullable = false)
    @Builder.Default
    private boolean defaultCard = false;

}
