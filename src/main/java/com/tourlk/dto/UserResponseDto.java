package com.tourlk.dto;

import com.tourlk.enums.Role;
import com.tourlk.enums.UserStatus;
import com.tourlk.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A user account as shown on the profile page and in the admin user list. No password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;

    /** Only meaningful for GUIDE/DRIVER; other roles stay NOT_SUBMITTED. */
    private VerificationStatus verificationStatus;
    private String licenceNumber;
    private LocalDate licenceExpiry;
    private String licenceDocumentUrl;
    private String licenceRejectionReason;
    private LocalDateTime licenceVerifiedAt;

}
