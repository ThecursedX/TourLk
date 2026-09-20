package com.tourlk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A guide's public reply to a review of one of their tour packages. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GuideReplyRequestDto {

    @NotBlank(message = "Reply is required")
    @Size(max = 1000, message = "Reply must be at most 1000 characters")
    private String reply;

}
