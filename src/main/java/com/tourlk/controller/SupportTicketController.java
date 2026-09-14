package com.tourlk.controller;

import com.tourlk.dto.SupportTicketRequestDto;
import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyRequestDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.TicketStatus;
import com.tourlk.service.SupportTicketService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Support ticket creation, threaded replies, and the ADMIN triage
 * workflow (assign/resolve/close/reopen). Any authenticated user of any
 * role can raise and reply to their own tickets — access beyond that
 * (viewing/replying to someone else's ticket) is enforced at the
 * service layer, not via role-based {@code @PreAuthorize}, since raisers
 * can be any role. See {@code SupportTicketServiceImpl#assertRaiserOrAdmin}.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Support Tickets", description = "Raise support tickets and manage them through to resolution")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<SupportTicketResponseDto> create(@Valid @RequestBody SupportTicketRequestDto request,
                                                             Authentication authentication) {
        SupportTicketResponseDto response = supportTicketService.createTicket(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/replies")
    public ResponseEntity<TicketReplyResponseDto> addReply(@PathVariable Long id,
                                                             @Valid @RequestBody TicketReplyRequestDto request,
                                                             Authentication authentication) {
        TicketReplyResponseDto response =
                supportTicketService.addReply(id, request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportTicketResponseDto> assign(@PathVariable Long id, Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(supportTicketService.assignTicket(id, currentUser.getId()));
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportTicketResponseDto> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(supportTicketService.resolveTicket(id));
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportTicketResponseDto> close(@PathVariable Long id) {
        return ResponseEntity.ok(supportTicketService.closeTicket(id));
    }

    @PutMapping("/{id}/reopen")
    public ResponseEntity<SupportTicketResponseDto> reopen(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.reopenTicket(id, currentUser(authentication)));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<SupportTicketResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(supportTicketService.getMyTickets(currentUser.getId()));
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportTicketResponseDto>> unassigned() {
        return ResponseEntity.ok(supportTicketService.getUnassignedTickets());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportTicketResponseDto>> all(
            @RequestParam(required = false) TicketStatus status) {
        return ResponseEntity.ok(supportTicketService.getAllTickets(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(supportTicketService.getTicketById(id, currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
