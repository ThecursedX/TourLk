package com.tourlk.service;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.LoginRequestDto;
import com.tourlk.dto.RegisterRequestDto;

public interface AuthService {

    AuthResponseDto register(RegisterRequestDto request);

    AuthResponseDto login(LoginRequestDto request);

}
