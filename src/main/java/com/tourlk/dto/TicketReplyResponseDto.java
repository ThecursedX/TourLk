package com.tourlk.dto;

import com.tourlk.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketReplyResponseDto {

    private Long id;
    private Long authorId;
    private String authorName;
    /** Lets the frontend style ADMIN replies differently from the raiser's own. */
    private Role authorRole;
    private String message;
    private LocalDateTime createdAt;

}
