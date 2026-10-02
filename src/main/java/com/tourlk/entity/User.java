package com.tourlk.entity;

import com.tourlk.enums.Role;
import com.tourlk.enums.UserStatus;
import com.tourlk.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Core user account, shared across every module. Feature modules should
 * reference users by id rather than duplicating account data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // ------------------------------------------------------------------
    // Licence verification (GUIDE / DRIVER only)
    // ------------------------------------------------------------------

    @Column(length = 50)
    private String licenceNumber;

    private LocalDate licenceExpiry;

    @Column(length = 1000)
    private String licenceDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.NOT_SUBMITTED;

    @Column(length = 1000)
    private String licenceRejectionReason;

    private Long licenceVerifiedById;

    private LocalDateTime licenceVerifiedAt;

    // ------------------------------------------------------------------
    // Stripe (saved payment methods)
    // ------------------------------------------------------------------

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

}
