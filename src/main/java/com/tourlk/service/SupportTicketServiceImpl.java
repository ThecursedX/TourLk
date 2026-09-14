package com.tourlk.service;

import com.tourlk.dto.SupportTicketRequestDto;
import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyRequestDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.SupportTicket;
import com.tourlk.entity.TicketReply;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.TicketPriority;
import com.tourlk.enums.TicketStatus;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.TicketAccessDeniedException;
import com.tourlk.exception.TicketClosedException;
import com.tourlk.repo.SupportTicketRepository;
import com.tourlk.repo.TicketReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ticket creation always seeds the thread with one {@code TicketReply}
 * (the initial message) so the reply thread is never empty — see
 * {@code createTicket}. Mail notifications (item 9) are best-effort:
 * {@code TicketMailService} silently no-ops when unconfigured, and is
 * only ever called after the ticket/reply row is already saved so a
 * mail failure can't roll back or block the actual operation.
 */
@Service
@RequiredArgsConstructor
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final TicketReplyRepository ticketReplyRepository;
    private final UserService userService;
    private final TicketMailService ticketMailService;

    @Override
    @Transactional
    public SupportTicketResponseDto createTicket(SupportTicketRequestDto request, User currentUser) {
        SupportTicket ticket = SupportTicket.builder()
                .raisedBy(currentUser)
                .subject(request.getSubject())
                .category(request.getCategory())
                .priority(request.getPriority() != null ? request.getPriority() : TicketPriority.MEDIUM)
                .build();
        ticket = supportTicketRepository.save(ticket);

        TicketReply initialReply = TicketReply.builder()
                .ticket(ticket)
                .author(currentUser)
                .message(request.getMessage())
                .build();
        ticketReplyRepository.save(initialReply);

        ticketMailService.notifyAdminsOfNewTicket(ticket);

        return toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketReplyResponseDto addReply(Long ticketId, TicketReplyRequestDto request, User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        assertRaiserOrAdmin(ticket, currentUser, "reply to");

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new TicketClosedException("This ticket is closed — reopen it before replying");
        }

        TicketReply reply = TicketReply.builder()
                .ticket(ticket)
                .author(currentUser)
                .message(request.getMessage())
                .build();
        reply = ticketReplyRepository.save(reply);

        if (currentUser.getRole() == Role.ADMIN) {
            ticketMailService.notifyRaiserOfReply(ticket, reply);
        }

        return toReplyResponse(reply);
    }

    @Override
    @Transactional
    public SupportTicketResponseDto assignTicket(Long ticketId, Long adminUserId) {
        SupportTicket ticket = getEntity(ticketId);
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new InvalidStatusTransitionException(
                    "Only OPEN tickets can be assigned, but this ticket is " + ticket.getStatus());
        }

        User admin = userService.getById(adminUserId);
        ticket.setAssignedTo(admin);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        return toResponse(supportTicketRepository.save(ticket));
    }

    @Override
    @Transactional
    public SupportTicketResponseDto resolveTicket(Long ticketId) {
        SupportTicket ticket = getEntity(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidStatusTransitionException("This ticket is already " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.RESOLVED);
        return toResponse(supportTicketRepository.save(ticket));
    }

    @Override
    @Transactional
    public SupportTicketResponseDto closeTicket(Long ticketId) {
        SupportTicket ticket = getEntity(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidStatusTransitionException("This ticket is already closed");
        }

        ticket.setStatus(TicketStatus.CLOSED);
        return toResponse(supportTicketRepository.save(ticket));
    }

    @Override
    @Transactional
    public SupportTicketResponseDto reopenTicket(Long ticketId, User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        assertRaiserOrAdmin(ticket, currentUser, "reopen");

        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new InvalidStatusTransitionException(
                    "Only RESOLVED or CLOSED tickets can be reopened, but this ticket is " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.OPEN);
        return toResponse(supportTicketRepository.save(ticket));
    }

    @Override
    public TicketDetailResponseDto getTicketById(Long id, User currentUser) {
        SupportTicket ticket = getEntity(id);
        assertRaiserOrAdmin(ticket, currentUser, "view");

        List<TicketReplyResponseDto> replies = ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(id).stream()
                .map(this::toReplyResponse)
                .toList();

        return TicketDetailResponseDto.builder()
                .ticket(toResponse(ticket))
                .replies(replies)
                .build();
    }

    @Override
    public List<SupportTicketResponseDto> getMyTickets(Long userId) {
        return supportTicketRepository.findByRaisedById(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<SupportTicketResponseDto> getAllTickets(TicketStatus statusFilter) {
        List<SupportTicket> tickets = statusFilter != null
                ? supportTicketRepository.findByStatus(statusFilter)
                : supportTicketRepository.findAll();

        return tickets.stream().map(this::toResponse).toList();
    }

    @Override
    public List<SupportTicketResponseDto> getUnassignedTickets() {
        return supportTicketRepository.findByAssignedToIsNull().stream()
                .map(this::toResponse)
                .toList();
    }

    private SupportTicket getEntity(Long id) {
        return supportTicketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found with id: " + id));
    }

    /**
     * The one place this module enforces its privacy rule: a ticket is
     * only ever visible/actionable by the tourist (or other user) who
     * raised it, or an ADMIN — never by another user guessing an id.
     */
    private void assertRaiserOrAdmin(SupportTicket ticket, User currentUser, String action) {
        boolean isRaiser = ticket.getRaisedBy().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isRaiser && !isAdmin) {
            throw new TicketAccessDeniedException("You do not have permission to " + action + " this ticket");
        }
    }

    private SupportTicketResponseDto toResponse(SupportTicket ticket) {
        return SupportTicketResponseDto.builder()
                .id(ticket.getId())
                .subject(ticket.getSubject())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .raisedById(ticket.getRaisedBy().getId())
                .raisedByName(ticket.getRaisedBy().getName())
                .assignedToId(ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null)
                .assignedToName(ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : null)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private TicketReplyResponseDto toReplyResponse(TicketReply reply) {
        return TicketReplyResponseDto.builder()
                .id(reply.getId())
                .authorId(reply.getAuthor().getId())
                .authorName(reply.getAuthor().getName())
                .authorRole(reply.getAuthor().getRole())
                .message(reply.getMessage())
                .createdAt(reply.getCreatedAt())
                .build();
    }

}
