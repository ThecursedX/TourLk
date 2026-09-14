package com.tourlk.service;

import com.tourlk.dto.SupportTicketRequestDto;
import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyRequestDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.TicketStatus;

import java.util.List;

public interface SupportTicketService {

    SupportTicketResponseDto createTicket(SupportTicketRequestDto request, User currentUser);

    TicketReplyResponseDto addReply(Long ticketId, TicketReplyRequestDto request, User currentUser);

    SupportTicketResponseDto assignTicket(Long ticketId, Long adminUserId);

    SupportTicketResponseDto resolveTicket(Long ticketId);

    SupportTicketResponseDto closeTicket(Long ticketId);

    SupportTicketResponseDto reopenTicket(Long ticketId, User currentUser);

    TicketDetailResponseDto getTicketById(Long id, User currentUser);

    List<SupportTicketResponseDto> getMyTickets(Long userId);

    List<SupportTicketResponseDto> getAllTickets(TicketStatus statusFilter);

    List<SupportTicketResponseDto> getUnassignedTickets();

}
