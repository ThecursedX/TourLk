package com.tourlk.service;

import com.tourlk.dto.SupportTicketRequestDto;
import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketAttachmentDownload;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyRequestDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.TicketStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SupportTicketService {

    SupportTicketResponseDto createTicket(SupportTicketRequestDto request, User currentUser);

    /** As above, with up to 3 attachments (images/PDF, 5 MB each) on the initial message. */
    SupportTicketResponseDto createTicket(SupportTicketRequestDto request, List<MultipartFile> files, User currentUser);

    TicketReplyResponseDto addReply(Long ticketId, TicketReplyRequestDto request, User currentUser);

    /**
     * Adds a reply with optional attachments. An admin reply moves the ticket to
     * WAITING_FOR_USER; the raiser's reply moves a WAITING_FOR_USER ticket back to IN_PROGRESS.
     */
    TicketReplyResponseDto addReply(Long ticketId, TicketReplyRequestDto request, List<MultipartFile> files,
                                    User currentUser);

    SupportTicketResponseDto assignTicket(Long ticketId, Long adminUserId);

    /** The raiser or an admin marks the ticket RESOLVED. */
    SupportTicketResponseDto resolveTicket(Long ticketId, User currentUser);

    /** The raiser withdraws a ticket that is not yet RESOLVED. */
    SupportTicketResponseDto withdrawTicket(Long ticketId, User currentUser);

    SupportTicketResponseDto closeTicket(Long ticketId);

    /** Raiser or admin reopens a RESOLVED/CLOSED ticket; only an admin can reopen a WITHDRAWN one. */
    SupportTicketResponseDto reopenTicket(Long ticketId, User currentUser);

    TicketDetailResponseDto getTicketById(Long id, User currentUser);

    /** Loads an attachment for download; only the raiser or an admin may. */
    TicketAttachmentDownload getAttachment(Long ticketId, Long attachmentId, User currentUser);

    List<SupportTicketResponseDto> getMyTickets(Long userId);

    List<SupportTicketResponseDto> getAllTickets(TicketStatus statusFilter);

    List<SupportTicketResponseDto> getUnassignedTickets();

}
