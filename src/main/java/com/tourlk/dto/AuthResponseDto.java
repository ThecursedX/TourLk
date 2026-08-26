package com.tourlk.dto;

import com.tourlk.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDto {

    private Long userId;
    private String name;
    private String email;
    private Role role;
    private String token;
    private String tokenType;

}
