package com.tourlk.dto;

import com.tourlk.enums.TicketCategory;
import com.tourlk.enums.TicketPriority;
import com.tourlk.enums.TicketStatus;
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
public class SupportTicketResponseDto {

    private Long id;
    private String subject;
    private TicketCategory category;
    private TicketPriority priority;
    private TicketStatus status;
    private Long raisedById;
    private String raisedByName;
    private Long assignedToId;
    private String assignedToName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
