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
import com.tourlk.enums.TicketCategory;
import com.tourlk.enums.TicketPriority;
import com.tourlk.enums.TicketStatus;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.TicketAccessDeniedException;
import com.tourlk.exception.TicketClosedException;
import com.tourlk.repo.SupportTicketRepository;
import com.tourlk.repo.TicketReplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SupportTicketServiceImpl}: ticket creation (with the
 * seeded initial reply), the raiser/admin access guard, the closed-ticket
 * reply block, and the assign/resolve/close/reopen status machine.
 * {@link TicketMailService} is mocked so no mail is ever attempted.
 */
@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;
    @Mock
    private TicketReplyRepository ticketReplyRepository;
    @Mock
    private UserService userService;
    @Mock
    private TicketMailService ticketMailService;

    @InjectMocks
    private SupportTicketServiceImpl service;

    private User raiser;
    private User stranger;
    private User admin;

    @BeforeEach
    void setUp() {
        raiser = User.builder().id(1L).name("Tess").email("tess@example.com").role(Role.TOURIST).build();
        stranger = User.builder().id(2L).name("Stan").role(Role.TOURIST).build();
        admin = User.builder().id(9L).name("Amy Admin").role(Role.ADMIN).build();
    }

    private SupportTicket ticket(TicketStatus status, User owner) {
        return SupportTicket.builder()
                .id(3L).raisedBy(owner).subject("Cannot pay").category(TicketCategory.PAYMENT)
                .priority(TicketPriority.HIGH).status(status)
                .build();
    }

    @Nested
    class CreateTicket {

        @Test
        void createTicket_savesTicketWithSeededReplyAndNotifiesAdmins() {
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
                SupportTicket t = inv.getArgument(0);
                t.setId(3L);
                return t;
            });

            SupportTicketRequestDto request = new SupportTicketRequestDto(
                    "Cannot pay", TicketCategory.PAYMENT, TicketPriority.HIGH, "Card keeps failing");
            SupportTicketResponseDto result = service.createTicket(request, raiser);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.OPEN);
            assertThat(result.getRaisedById()).isEqualTo(1L);

            ArgumentCaptor<TicketReply> reply = ArgumentCaptor.forClass(TicketReply.class);
            verify(ticketReplyRepository).save(reply.capture());
            assertThat(reply.getValue().getMessage()).isEqualTo("Card keeps failing");
            verify(ticketMailService).notifyAdminsOfNewTicket(any(SupportTicket.class));
        }

        @Test
        void createTicket_noPriorityGiven_defaultsToMedium() {
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
                SupportTicket t = inv.getArgument(0);
                t.setId(3L);
                return t;
            });

            SupportTicketRequestDto request = new SupportTicketRequestDto(
                    "Question", TicketCategory.OTHER, null, "How do I cancel?");
            SupportTicketResponseDto result = service.createTicket(request, raiser);

            assertThat(result.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        }
    }

    @Nested
    class AddReply {

        @Test
        void addReply_byRaiserOnOpenTicket_savesReply() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> {
                TicketReply r = inv.getArgument(0);
                r.setId(11L);
                return r;
            });

            TicketReplyResponseDto result = service.addReply(3L, new TicketReplyRequestDto("Any update?"), raiser);

            assertThat(result.getMessage()).isEqualTo("Any update?");
            assertThat(result.getAuthorId()).isEqualTo(1L);
            verify(ticketMailService, never()).notifyRaiserOfReply(any(), any());
        }

        @Test
        void addReply_byAdmin_notifiesRaiser() {
            when(supportTicketRepository.findById(3L))
                    .thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS, raiser)));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addReply(3L, new TicketReplyRequestDto("Looking into it"), admin);

            verify(ticketMailService).notifyRaiserOfReply(any(SupportTicket.class), any(TicketReply.class));
        }

        @Test
        void addReply_byStranger_throwsTicketAccessDenied() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.addReply(3L, new TicketReplyRequestDto("Sneaky"), stranger))
                    .isInstanceOf(TicketAccessDeniedException.class);
            verify(ticketReplyRepository, never()).save(any());
        }

        @Test
        void addReply_onClosedTicket_throwsTicketClosed() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.CLOSED, raiser)));

            assertThatThrownBy(() -> service.addReply(3L, new TicketReplyRequestDto("Hello?"), raiser))
                    .isInstanceOf(TicketClosedException.class);
            verify(ticketReplyRepository, never()).save(any());
        }

        @Test
        void addReply_ticketNotFound_throwsResourceNotFound() {
            when(supportTicketRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addReply(404L, new TicketReplyRequestDto("x"), raiser))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class TriageWorkflow {

        @Test
        void assignTicket_openTicket_assignsAdminAndMovesToInProgress() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));
            when(userService.getById(9L)).thenReturn(admin);
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            SupportTicketResponseDto result = service.assignTicket(3L, 9L);

            assertThat(result.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(result.getAssignedToId()).isEqualTo(9L);
        }

        @Test
        void assignTicket_notOpen_throwsInvalidStatusTransition() {
            when(supportTicketRepository.findById(3L))
                    .thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS, raiser)));

            assertThatThrownBy(() -> service.assignTicket(3L, 9L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void resolveTicket_inProgress_becomesResolved() {
            when(supportTicketRepository.findById(3L))
                    .thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS, raiser)));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.resolveTicket(3L).getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        void resolveTicket_alreadyResolved_throwsInvalidStatusTransition() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.RESOLVED, raiser)));

            assertThatThrownBy(() -> service.resolveTicket(3L))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void closeTicket_inProgress_becomesClosed() {
            when(supportTicketRepository.findById(3L))
                    .thenReturn(Optional.of(ticket(TicketStatus.IN_PROGRESS, raiser)));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.closeTicket(3L).getStatus()).isEqualTo(TicketStatus.CLOSED);
        }

        @Test
        void reopenTicket_resolvedByRaiser_becomesOpen() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.RESOLVED, raiser)));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.reopenTicket(3L, raiser).getStatus()).isEqualTo(TicketStatus.OPEN);
        }

        @Test
        void reopenTicket_stillOpen_throwsInvalidStatusTransition() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.reopenTicket(3L, raiser))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void reopenTicket_byStranger_throwsTicketAccessDenied() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.CLOSED, raiser)));

            assertThatThrownBy(() -> service.reopenTicket(3L, stranger))
                    .isInstanceOf(TicketAccessDeniedException.class);
        }
    }

    @Nested
    class Queries {

        @Test
        void getTicketById_byRaiser_returnsTicketWithReplies() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));
            TicketReply reply = TicketReply.builder().id(11L).author(raiser).message("Initial").build();
            when(ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(reply));

            TicketDetailResponseDto result = service.getTicketById(3L, raiser);

            assertThat(result.getTicket().getId()).isEqualTo(3L);
            assertThat(result.getReplies()).hasSize(1);
        }

        @Test
        void getTicketById_byStranger_throwsTicketAccessDenied() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.getTicketById(3L, stranger))
                    .isInstanceOf(TicketAccessDeniedException.class);
        }

        @Test
        void getTicketById_byAdmin_isAllowed() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));
            when(ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(3L)).thenReturn(List.of());

            assertThat(service.getTicketById(3L, admin).getTicket().getId()).isEqualTo(3L);
        }
    }
}
