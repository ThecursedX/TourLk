package com.tourlk.service;

import com.tourlk.dto.SupportTicketRequestDto;
import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketAttachmentDownload;
import com.tourlk.dto.TicketAttachmentResponseDto;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyRequestDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.SupportTicket;
import com.tourlk.entity.TicketAttachment;
import com.tourlk.entity.TicketReply;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.TicketPriority;
import com.tourlk.enums.TicketStatus;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.TicketAccessDeniedException;
import com.tourlk.exception.TicketClosedException;
import com.tourlk.repo.SupportTicketRepository;
import com.tourlk.repo.TicketAttachmentRepository;
import com.tourlk.repo.TicketReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final TicketAttachmentRepository ticketAttachmentRepository;
    private final TicketAttachmentStorage attachmentStorage;
    private final UserService userService;
    private final TicketMailService ticketMailService;
    private final NotificationService notificationService;

    /** Statuses in which a ticket is still being worked on (neither finished nor abandoned). */
    private static final Set<TicketStatus> ACTIVE = EnumSet.of(
            TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_USER);

    @Override
    public SupportTicketResponseDto createTicket(SupportTicketRequestDto request, User currentUser) {
        return createTicket(request, List.of(), currentUser);
    }

    @Override
    @Transactional
    public SupportTicketResponseDto createTicket(SupportTicketRequestDto request, List<MultipartFile> files,
                                                 User currentUser) {
        // Validate every file before anything is written, so a bad upload leaves no trace.
        List<MultipartFile> uploads = validUploads(files);

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
        initialReply = ticketReplyRepository.save(initialReply);
        saveAttachments(ticket, initialReply, currentUser, uploads);

        ticketMailService.notifyAdminsOfNewTicket(ticket);

        return toResponse(ticket);
    }

    @Override
    public TicketReplyResponseDto addReply(Long ticketId, TicketReplyRequestDto request, User currentUser) {
        return addReply(ticketId, request, List.of(), currentUser);
    }

    @Override
    @Transactional
    public TicketReplyResponseDto addReply(Long ticketId, TicketReplyRequestDto request, List<MultipartFile> files,
                                           User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        assertRaiserOrAdmin(ticket, currentUser, "reply to");

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new TicketClosedException("This ticket is closed — reopen it before replying");
        }
        if (ticket.getStatus() == TicketStatus.WITHDRAWN) {
            throw new TicketClosedException("This ticket was withdrawn — it must be reopened before replying");
        }
        List<MultipartFile> uploads = validUploads(files);

        TicketReply reply = TicketReply.builder()
                .ticket(ticket)
                .author(currentUser)
                .message(request.getMessage())
                .build();
        reply = ticketReplyRepository.save(reply);
        List<TicketAttachment> attachments = saveAttachments(ticket, reply, currentUser, uploads);

        TicketStatus after = statusAfterReply(ticket.getStatus(), currentUser);
        boolean statusChanged = after != ticket.getStatus();
        if (statusChanged) {
            ticket.setStatus(after);
        }
        boolean pickedUp = false;
        if (currentUser.getRole() == Role.ADMIN && ticket.getAssignedTo() == null) {
            // Whoever answers a ticket picks it up, so it does not sit in the unassigned queue.
            ticket.setAssignedTo(currentUser);
            pickedUp = true;
        }
        if (statusChanged || pickedUp) {
            supportTicketRepository.save(ticket);
        }

        if (currentUser.getRole() == Role.ADMIN) {
            ticketMailService.notifyRaiserOfReply(ticket, reply);
            notificationService.notify(ticket.getRaisedBy(), NotificationType.TICKET_REPLY,
                    "New reply on your ticket", ticket.getSubject(), "/support/" + ticket.getId());
        } else if (ticket.getAssignedTo() != null) {
            notificationService.notify(ticket.getAssignedTo(), NotificationType.TICKET_REPLY,
                    "New reply on ticket #" + ticket.getId(), ticket.getSubject(), "/admin/tickets");
        }

        if (statusChanged) {
            notifyStatusChange(ticket, currentUser);
        }

        return toReplyResponse(reply, attachments);
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
        SupportTicket saved = supportTicketRepository.save(ticket);
        notifyStatusChange(saved, admin);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupportTicketResponseDto resolveTicket(Long ticketId, User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        assertRaiserOrAdmin(ticket, currentUser, "resolve");
        if (!ACTIVE.contains(ticket.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Only open tickets can be resolved, but this ticket is " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.RESOLVED);
        SupportTicket saved = supportTicketRepository.save(ticket);
        notifyStatusChange(saved, currentUser);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupportTicketResponseDto withdrawTicket(Long ticketId, User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        if (!ticket.getRaisedBy().getId().equals(currentUser.getId())) {
            throw new TicketAccessDeniedException("Only the person who raised a ticket can withdraw it");
        }
        if (!ACTIVE.contains(ticket.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "A ticket can only be withdrawn before it is resolved, but this ticket is " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.WITHDRAWN);
        SupportTicket saved = supportTicketRepository.save(ticket);
        notifyStatusChange(saved, currentUser);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupportTicketResponseDto closeTicket(Long ticketId) {
        SupportTicket ticket = getEntity(ticketId);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidStatusTransitionException("This ticket is already closed");
        }

        ticket.setStatus(TicketStatus.CLOSED);
        SupportTicket saved = supportTicketRepository.save(ticket);
        notifyStatusChange(saved, null);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SupportTicketResponseDto reopenTicket(Long ticketId, User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        assertRaiserOrAdmin(ticket, currentUser, "reopen");

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean reopenable = ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED
                || (isAdmin && ticket.getStatus() == TicketStatus.WITHDRAWN);
        if (!reopenable) {
            throw new InvalidStatusTransitionException(
                    "Only RESOLVED or CLOSED tickets can be reopened (or WITHDRAWN ones, by an admin), but this "
                            + "ticket is " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.OPEN);
        SupportTicket saved = supportTicketRepository.save(ticket);
        notifyStatusChange(saved, currentUser);
        return toResponse(saved);
    }

    @Override
    public TicketDetailResponseDto getTicketById(Long id, User currentUser) {
        SupportTicket ticket = getEntity(id);
        assertRaiserOrAdmin(ticket, currentUser, "view");

        Map<Long, List<TicketAttachment>> attachmentsByReply = ticketAttachmentRepository.findByTicketId(id).stream()
                .collect(Collectors.groupingBy(a -> a.getReply().getId()));
        List<TicketReplyResponseDto> replies = ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(id).stream()
                .map(reply -> toReplyResponse(reply, attachmentsByReply.getOrDefault(reply.getId(), List.of())))
                .toList();

        return TicketDetailResponseDto.builder()
                .ticket(toResponse(ticket))
                .replies(replies)
                .build();
    }

    @Override
    public TicketAttachmentDownload getAttachment(Long ticketId, Long attachmentId, User currentUser) {
        SupportTicket ticket = getEntity(ticketId);
        assertRaiserOrAdmin(ticket, currentUser, "download files from");

        // An id from another ticket must look exactly like a missing one.
        TicketAttachment attachment = ticketAttachmentRepository.findById(attachmentId)
                .filter(a -> a.getTicket().getId().equals(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with id: " + attachmentId));

        return new TicketAttachmentDownload(attachmentStorage.load(attachment.getStoredPath()),
                attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes());
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

    private List<MultipartFile> validUploads(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return attachmentStorage.validate(files);
    }

    /** Admin reply -> WAITING_FOR_USER; the raiser's reply to a WAITING_FOR_USER ticket -> IN_PROGRESS. */
    private TicketStatus statusAfterReply(TicketStatus current, User author) {
        if (author.getRole() == Role.ADMIN) {
            return ACTIVE.contains(current) ? TicketStatus.WAITING_FOR_USER : current;
        }
        return current == TicketStatus.WAITING_FOR_USER ? TicketStatus.IN_PROGRESS : current;
    }

    /**
     * Writes the files to disk and records them against the reply. If anything fails
     * (now, or when the surrounding transaction later rolls back) the files are removed,
     * so no orphans are left on disk.
     */
    private List<TicketAttachment> saveAttachments(SupportTicket ticket, TicketReply reply, User uploader,
                                                   List<MultipartFile> uploads) {
        if (uploads.isEmpty()) {
            return List.of();
        }

        List<TicketAttachmentStorage.StoredFile> stored = new ArrayList<>();
        try {
            List<TicketAttachment> saved = new ArrayList<>();
            for (MultipartFile upload : uploads) {
                TicketAttachmentStorage.StoredFile file = attachmentStorage.store(ticket.getId(), upload);
                stored.add(file);
                saved.add(ticketAttachmentRepository.save(TicketAttachment.builder()
                        .ticket(ticket).reply(reply).uploadedBy(uploader)
                        .fileName(file.fileName()).contentType(file.contentType())
                        .storedPath(file.storedPath()).sizeBytes(file.sizeBytes())
                        .build()));
            }

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            stored.forEach(file -> attachmentStorage.deleteQuietly(file.storedPath()));
                        }
                    }
                });
            }
            return saved;
        } catch (RuntimeException e) {
            stored.forEach(file -> attachmentStorage.deleteQuietly(file.storedPath()));
            throw e;
        }
    }

    /**
     * In-app notice of a status change to the other side of the conversation: the
     * raiser when staff made the change, the assignee (or every admin, if nobody
     * has picked it up) when the raiser did.
     */
    private void notifyStatusChange(SupportTicket ticket, User actor) {
        String title = "Ticket #" + ticket.getId() + " is now " + label(ticket.getStatus());
        boolean byRaiser = actor != null && ticket.getRaisedBy().getId().equals(actor.getId());

        if (byRaiser) {
            if (ticket.getAssignedTo() != null) {
                notificationService.notify(ticket.getAssignedTo(), NotificationType.TICKET_STATUS_CHANGED,
                        title, ticket.getSubject(), "/admin/tickets");
            } else {
                notificationService.notifyAdmins(NotificationType.TICKET_STATUS_CHANGED,
                        title, ticket.getSubject(), "/admin/tickets");
            }
        } else {
            notificationService.notify(ticket.getRaisedBy(), NotificationType.TICKET_STATUS_CHANGED,
                    title, ticket.getSubject(), "/support/" + ticket.getId());
        }
    }

    private String label(TicketStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
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

    private TicketReplyResponseDto toReplyResponse(TicketReply reply, List<TicketAttachment> attachments) {
        return TicketReplyResponseDto.builder()
                .attachments(attachments.stream()
                        .map(a -> TicketAttachmentResponseDto.builder()
                                .id(a.getId()).fileName(a.getFileName())
                                .contentType(a.getContentType()).sizeBytes(a.getSizeBytes()).build())
                        .toList())
                .id(reply.getId())
                .authorId(reply.getAuthor().getId())
                .authorName(reply.getAuthor().getName())
                .authorRole(reply.getAuthor().getRole())
                .message(reply.getMessage())
                .createdAt(reply.getCreatedAt())
                .build();
    }

}
