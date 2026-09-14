package com.suretyseven.docflow.service;

import com.suretyseven.docflow.dto.request.LoginRequestDto;
import com.suretyseven.docflow.dto.response.LoginResponseDto;

public interface AuthService {

    LoginResponseDto login(LoginRequestDto loginRequestDto);
}
