package com.tourlk.repo;

import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.UserStatus;
import com.tourlk.enums.VerificationStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    long countByRoleAndStatus(Role role, UserStatus status);

    /** Admin search: the term may appear in the name or the email (case-insensitive). */
    List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Sort sort);

    List<User> findByVerificationStatus(VerificationStatus verificationStatus);

    /** GUIDE/DRIVER accounts with the given status whose licence expires on or before the cutoff. */
    List<User> findByVerificationStatusAndRoleInAndLicenceExpiryLessThanEqual(
            VerificationStatus verificationStatus, java.util.Collection<Role> roles, java.time.LocalDate cutoff);

}
