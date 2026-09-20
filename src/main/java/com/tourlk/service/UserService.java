package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.UpdateProfileRequestDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;

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

}
