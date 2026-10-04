package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.ChangePasswordRequestDto;
import com.tourlk.dto.LicenceDocumentDownload;
import com.tourlk.dto.LicenceSubmitRequestDto;
import com.tourlk.dto.UpdateProfileRequestDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {

    User getById(Long id);

    User getByEmail(String email);

    boolean emailExists(String email);

    User save(User user);

    // ------------------------------------------------------------------
    // Self-service profile
    // ------------------------------------------------------------------

    UserResponseDto getProfile(User currentUser);

    /**
     * Updates the caller's own name, email and phone. The JWT subject is the
     * email, so a fresh token is returned with the response (the old one
     * stops resolving to a user once the email changes).
     *
     * @throws com.tourlk.exception.BadRequestException if the email belongs to another account
     */
    AuthResponseDto updateProfile(User currentUser, UpdateProfileRequestDto request);

    /**
     * Changes the caller's own password after verifying the current one.
     *
     * @throws com.tourlk.exception.BadRequestException if currentPassword doesn't match
     */
    void changePassword(User currentUser, ChangePasswordRequestDto request);

    /**
     * Deactivates the caller's own account after re-checking their password.
     * The account can no longer sign in; only an admin can reactivate it.
     *
     * @throws com.tourlk.exception.BadRequestException if the password is wrong, or the caller is
     *                                                  the last active admin
     */
    void deactivateOwnAccount(User currentUser, String password);

    // ------------------------------------------------------------------
    // Admin user management
    // ------------------------------------------------------------------

    /** All users, or those whose name or email contains {@code search} when it is not blank. */
    List<UserResponseDto> searchUsers(String search);

    UserResponseDto getUserById(Long id);

    /** @throws com.tourlk.exception.BadRequestException if the admin targets their own account */
    UserResponseDto deactivateUser(Long id, User currentUser);

    UserResponseDto reactivateUser(Long id);

    /**
     * Permanently deletes an account. Only DEACTIVATED accounts can be
     * deleted, never the caller's own, and never one that other records
     * (bookings, listings, reviews...) still reference.
     */
    void deleteUser(Long id, User currentUser);

    // ------------------------------------------------------------------
    // Licence verification (GUIDE / DRIVER)
    // ------------------------------------------------------------------

    /**
     * Submits (or resubmits) the caller's licence details for admin review.
     *
     * @throws com.tourlk.exception.BadRequestException if the caller isn't a GUIDE or DRIVER
     */
    UserResponseDto submitLicence(User currentUser, LicenceSubmitRequestDto request, MultipartFile file);

    /**
     * The stored licence document of {@code userId}, for that user or an ADMIN only.
     *
     * @throws org.springframework.security.access.AccessDeniedException for anyone else
     * @throws com.tourlk.exception.ResourceNotFoundException if no uploaded document exists
     */
    LicenceDocumentDownload getLicenceDocument(Long userId, User requester);

    /** Every GUIDE/DRIVER with a licence currently awaiting review. */
    List<UserResponseDto> getPendingLicences();

    /** @throws com.tourlk.exception.InvalidStatusTransitionException if the licence isn't PENDING */
    UserResponseDto verifyLicence(Long userId, User admin);

    /** @throws com.tourlk.exception.InvalidStatusTransitionException if the licence isn't PENDING */
    UserResponseDto rejectLicence(Long userId, String reason, User admin);

}
