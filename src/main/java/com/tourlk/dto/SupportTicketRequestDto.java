package com.tourlk.dto;

import com.tourlk.enums.TicketCategory;
import com.tourlk.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketRequestDto {

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must be at most 200 characters")
    private String subject;

    @NotNull(message = "Category is required")
    private TicketCategory category;

    /** Optional — defaults to MEDIUM when not supplied. */
    private TicketPriority priority;

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String message;

}
