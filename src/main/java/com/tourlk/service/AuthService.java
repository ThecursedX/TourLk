package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.ForgotPasswordRequestDto;
import com.tourlk.dto.LoginRequestDto;
import com.tourlk.dto.RegisterRequestDto;
import com.tourlk.dto.ResetPasswordRequestDto;

public interface AuthService {

    AuthResponseDto register(RegisterRequestDto request);

    AuthResponseDto login(LoginRequestDto request);

    /** Always completes normally, whether or not the email belongs to an account, to avoid account enumeration. */
    void forgotPassword(ForgotPasswordRequestDto request);

    /**
     * Sets a new password for the account behind a valid, unused, unexpired reset token, and
     * logs the user in with a fresh JWT.
     *
     * @throws com.tourlk.exception.BadRequestException if the token is invalid, expired or already used
     */
    AuthResponseDto resetPassword(ResetPasswordRequestDto request);

}
