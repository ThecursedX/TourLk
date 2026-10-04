package com.tourlk.service;

import com.tourlk.dto.SupportTicketRequestDto;
import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketAttachmentDownload;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyRequestDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.SupportTicket;
import com.tourlk.entity.TicketAttachment;
import com.tourlk.entity.TicketReply;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.TicketCategory;
import com.tourlk.enums.TicketPriority;
import com.tourlk.enums.TicketStatus;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.InvalidStatusTransitionException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.exception.TicketAccessDeniedException;
import com.tourlk.exception.TicketClosedException;
import com.tourlk.repo.SupportTicketRepository;
import com.tourlk.repo.TicketAttachmentRepository;
import com.tourlk.repo.TicketReplyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private TicketAttachmentRepository ticketAttachmentRepository;
    @Mock
    private TicketAttachmentStorage attachmentStorage;
    @Mock
    private UserService userService;
    @Mock
    private TicketMailService ticketMailService;
    @Mock
    private NotificationService notificationService;

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

            assertThat(service.resolveTicket(3L, admin).getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        void resolveTicket_alreadyResolved_throwsInvalidStatusTransition() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.RESOLVED, raiser)));

            assertThatThrownBy(() -> service.resolveTicket(3L, admin))
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
    class StatusWorkflow {

        private void stubTicket(TicketStatus status) {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(status, raiser)));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));
            org.mockito.Mockito.lenient().when(ticketReplyRepository.save(any(TicketReply.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }

        private TicketReplyRequestDto reply() {
            return new TicketReplyRequestDto("Please send a screenshot");
        }

        @Test
        void addReply_byAdminOnOpenTicket_movesToWaitingForUser_assignsAdmin_notifiesRaiser() {
            stubTicket(TicketStatus.OPEN);
            SupportTicket ticket = ticket(TicketStatus.OPEN, raiser);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));

            service.addReply(3L, reply(), admin);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_USER);
            assertThat(ticket.getAssignedTo()).isSameAs(admin);
            verify(notificationService).notify(eq(raiser), eq(NotificationType.TICKET_STATUS_CHANGED),
                    eq("Ticket #3 is now waiting for user"), any(), eq("/support/3"));
        }

        @Test
        void addReply_byAdminOnInProgressTicket_keepsExistingAssignee() {
            User other = User.builder().id(8L).name("Other Admin").role(Role.ADMIN).build();
            SupportTicket ticket = ticket(TicketStatus.IN_PROGRESS, raiser);
            ticket.setAssignedTo(other);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addReply(3L, reply(), admin);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.WAITING_FOR_USER);
            assertThat(ticket.getAssignedTo()).isSameAs(other);
        }

        @Test
        void addReply_byRaiserOnWaitingForUser_movesToInProgress_notifiesAssignee() {
            SupportTicket ticket = ticket(TicketStatus.WAITING_FOR_USER, raiser);
            ticket.setAssignedTo(admin);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addReply(3L, new TicketReplyRequestDto("Here you go"), raiser);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            verify(notificationService).notify(eq(admin), eq(NotificationType.TICKET_STATUS_CHANGED),
                    eq("Ticket #3 is now in progress"), any(), eq("/admin/tickets"));
        }

        @Test
        void addReply_byRaiserOnOpenTicket_leavesStatusUnchanged() {
            SupportTicket ticket = ticket(TicketStatus.OPEN, raiser);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addReply(3L, new TicketReplyRequestDto("More detail"), raiser);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
            verify(notificationService, never()).notify(any(), eq(NotificationType.TICKET_STATUS_CHANGED), any(), any(), any());
        }

        @Test
        void addReply_byAdminOnResolvedTicket_leavesStatusUnchanged() {
            SupportTicket ticket = ticket(TicketStatus.RESOLVED, raiser);
            ticket.setAssignedTo(admin);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addReply(3L, reply(), admin);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        void addReply_onWithdrawnTicket_throwsTicketClosed() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.WITHDRAWN, raiser)));

            assertThatThrownBy(() -> service.addReply(3L, reply(), raiser))
                    .isInstanceOf(TicketClosedException.class);
            verify(ticketReplyRepository, never()).save(any());
        }

        @Test
        void withdrawTicket_activeStatuses_becomeWithdrawn_andNotifyAllAdminsWhenUnassigned() {
            for (TicketStatus status : List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_FOR_USER)) {
                SupportTicket ticket = ticket(status, raiser);
                when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
                when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

                assertThat(service.withdrawTicket(3L, raiser).getStatus()).isEqualTo(TicketStatus.WITHDRAWN);
            }
            verify(notificationService, org.mockito.Mockito.times(3)).notifyAdmins(
                    eq(NotificationType.TICKET_STATUS_CHANGED), eq("Ticket #3 is now withdrawn"), any(), eq("/admin/tickets"));
        }

        @Test
        void withdrawTicket_resolvedOrClosed_throwsInvalidStatusTransition() {
            for (TicketStatus status : List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.WITHDRAWN)) {
                when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(status, raiser)));

                assertThatThrownBy(() -> service.withdrawTicket(3L, raiser))
                        .as(status.name()).isInstanceOf(InvalidStatusTransitionException.class);
            }
        }

        @Test
        void withdrawTicket_byAdminOrStranger_throwsTicketAccessDenied() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.withdrawTicket(3L, admin)).isInstanceOf(TicketAccessDeniedException.class);
            assertThatThrownBy(() -> service.withdrawTicket(3L, stranger)).isInstanceOf(TicketAccessDeniedException.class);
        }

        @Test
        void resolveTicket_byRaiserWaitingForUser_becomesResolved_andNotifiesAssignee() {
            SupportTicket ticket = ticket(TicketStatus.WAITING_FOR_USER, raiser);
            ticket.setAssignedTo(admin);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.resolveTicket(3L, raiser).getStatus()).isEqualTo(TicketStatus.RESOLVED);
            verify(notificationService).notify(eq(admin), eq(NotificationType.TICKET_STATUS_CHANGED), any(), any(), any());
        }

        @Test
        void resolveTicket_byStranger_throwsTicketAccessDenied() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.resolveTicket(3L, stranger))
                    .isInstanceOf(TicketAccessDeniedException.class);
        }

        @Test
        void resolveTicket_withdrawn_throwsInvalidStatusTransition() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.WITHDRAWN, raiser)));

            assertThatThrownBy(() -> service.resolveTicket(3L, raiser))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void reopenTicket_withdrawnByAdmin_becomesOpen_andNotifiesRaiser() {
            stubTicket(TicketStatus.WITHDRAWN);

            assertThat(service.reopenTicket(3L, admin).getStatus()).isEqualTo(TicketStatus.OPEN);
            verify(notificationService).notify(eq(raiser), eq(NotificationType.TICKET_STATUS_CHANGED),
                    eq("Ticket #3 is now open"), any(), eq("/support/3"));
        }

        @Test
        void reopenTicket_withdrawnByRaiser_throwsInvalidStatusTransition() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.WITHDRAWN, raiser)));

            assertThatThrownBy(() -> service.reopenTicket(3L, raiser))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        void assignTicket_notifiesRaiserOfInProgress() {
            stubTicket(TicketStatus.OPEN);
            when(userService.getById(9L)).thenReturn(admin);

            service.assignTicket(3L, 9L);

            verify(notificationService).notify(eq(raiser), eq(NotificationType.TICKET_STATUS_CHANGED),
                    eq("Ticket #3 is now in progress"), any(), eq("/support/3"));
        }

        @Test
        void closeTicket_notifiesRaiser() {
            stubTicket(TicketStatus.IN_PROGRESS);

            service.closeTicket(3L);

            verify(notificationService).notify(eq(raiser), eq(NotificationType.TICKET_STATUS_CHANGED),
                    eq("Ticket #3 is now closed"), any(), eq("/support/3"));
        }
    }

    @Nested
    class Attachments {

        private final MockMultipartFile png = new MockMultipartFile(
                "files", "shot.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

        private TicketAttachmentStorage.StoredFile stored() {
            return new TicketAttachmentStorage.StoredFile("3/abc.png", "shot.png", "image/png", 8);
        }

        @Test
        void createTicket_withFiles_validatesFirst_thenStoresAndLinksToInitialReply() {
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
                SupportTicket t = inv.getArgument(0);
                t.setId(3L);
                return t;
            });
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));
            when(attachmentStorage.validate(any())).thenReturn(List.of(png));
            when(attachmentStorage.store(eq(3L), eq(png))).thenReturn(stored());
            when(ticketAttachmentRepository.save(any(TicketAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createTicket(new SupportTicketRequestDto(
                    "Cannot pay", TicketCategory.PAYMENT, null, "See screenshot"), List.of(png), raiser);

            ArgumentCaptor<TicketAttachment> saved = ArgumentCaptor.forClass(TicketAttachment.class);
            verify(ticketAttachmentRepository).save(saved.capture());
            assertThat(saved.getValue().getFileName()).isEqualTo("shot.png");
            assertThat(saved.getValue().getStoredPath()).isEqualTo("3/abc.png");
            assertThat(saved.getValue().getUploadedBy()).isSameAs(raiser);
            assertThat(saved.getValue().getReply()).isNotNull();
        }

        @Test
        void createTicket_invalidFile_savesNothing() {
            when(attachmentStorage.validate(any())).thenThrow(new BadRequestException("not allowed"));

            assertThatThrownBy(() -> service.createTicket(new SupportTicketRequestDto(
                    "Cannot pay", TicketCategory.PAYMENT, null, "x"), List.of(png), raiser))
                    .isInstanceOf(BadRequestException.class);
            verify(supportTicketRepository, never()).save(any());
            verify(attachmentStorage, never()).store(any(), any());
        }

        @Test
        void createTicket_storageFailsMidway_removesFilesAlreadyWritten() {
            when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> {
                SupportTicket t = inv.getArgument(0);
                t.setId(3L);
                return t;
            });
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> inv.getArgument(0));
            MockMultipartFile second = new MockMultipartFile("files", "b.png", "image/png", new byte[] {1});
            when(attachmentStorage.validate(any())).thenReturn(List.of(png, second));
            when(attachmentStorage.store(eq(3L), eq(png))).thenReturn(stored());
            when(attachmentStorage.store(eq(3L), eq(second))).thenThrow(new IllegalStateException("disk full"));
            when(ticketAttachmentRepository.save(any(TicketAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> service.createTicket(new SupportTicketRequestDto(
                    "Cannot pay", TicketCategory.PAYMENT, null, "x"), List.of(png, second), raiser))
                    .isInstanceOf(IllegalStateException.class);
            verify(attachmentStorage).deleteQuietly("3/abc.png");
        }

        @Test
        void addReply_withFiles_returnsAttachmentsOnTheReply() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));
            when(ticketReplyRepository.save(any(TicketReply.class))).thenAnswer(inv -> {
                TicketReply r = inv.getArgument(0);
                r.setId(11L);
                return r;
            });
            when(attachmentStorage.validate(any())).thenReturn(List.of(png));
            when(attachmentStorage.store(eq(3L), eq(png))).thenReturn(stored());
            when(ticketAttachmentRepository.save(any(TicketAttachment.class))).thenAnswer(inv -> {
                TicketAttachment a = inv.getArgument(0);
                a.setId(5L);
                return a;
            });

            TicketReplyResponseDto result = service.addReply(3L, new TicketReplyRequestDto("Screenshot"), List.of(png), raiser);

            assertThat(result.getAttachments()).hasSize(1);
            assertThat(result.getAttachments().get(0).getId()).isEqualTo(5L);
            assertThat(result.getAttachments().get(0).getFileName()).isEqualTo("shot.png");
        }

        @Test
        void addReply_byStrangerWithFiles_isRejectedBeforeAnyFileIsTouched() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.addReply(3L, new TicketReplyRequestDto("x"), List.of(png), stranger))
                    .isInstanceOf(TicketAccessDeniedException.class);
            verify(attachmentStorage, never()).validate(any());
            verify(attachmentStorage, never()).store(any(), any());
        }

        private TicketAttachment attachment(Long id, SupportTicket ticket) {
            return TicketAttachment.builder().id(id).ticket(ticket)
                    .reply(TicketReply.builder().id(11L).build()).uploadedBy(raiser)
                    .fileName("shot.png").contentType("image/png").storedPath("3/abc.png").sizeBytes(8).build();
        }

        @Test
        void getAttachment_byRaiserAndAdmin_returnsDownload() {
            SupportTicket ticket = ticket(TicketStatus.OPEN, raiser);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            when(ticketAttachmentRepository.findById(5L)).thenReturn(Optional.of(attachment(5L, ticket)));
            when(attachmentStorage.load("3/abc.png")).thenReturn(new ByteArrayResource(new byte[8]));

            TicketAttachmentDownload byRaiser = service.getAttachment(3L, 5L, raiser);
            TicketAttachmentDownload byAdmin = service.getAttachment(3L, 5L, admin);

            assertThat(byRaiser.fileName()).isEqualTo("shot.png");
            assertThat(byRaiser.contentType()).isEqualTo("image/png");
            assertThat(byAdmin.sizeBytes()).isEqualTo(8);
        }

        @Test
        void getAttachment_byStranger_throwsTicketAccessDenied() {
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, raiser)));

            assertThatThrownBy(() -> service.getAttachment(3L, 5L, stranger))
                    .isInstanceOf(TicketAccessDeniedException.class);
            verify(attachmentStorage, never()).load(any());
        }

        @Test
        void getAttachment_belongingToAnotherTicket_isNotFound() {
            SupportTicket mine = ticket(TicketStatus.OPEN, raiser);
            SupportTicket theirs = SupportTicket.builder().id(99L).raisedBy(stranger).build();
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(mine));
            when(ticketAttachmentRepository.findById(5L)).thenReturn(Optional.of(attachment(5L, theirs)));

            assertThatThrownBy(() -> service.getAttachment(3L, 5L, raiser))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(attachmentStorage, never()).load(any());
        }

        @Test
        void getTicketById_groupsAttachmentsUnderTheirReply() {
            SupportTicket ticket = ticket(TicketStatus.OPEN, raiser);
            when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(ticket));
            TicketReply first = TicketReply.builder().id(11L).author(raiser).message("Initial").build();
            TicketReply second = TicketReply.builder().id(12L).author(admin).message("Reply").build();
            when(ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(first, second));
            when(ticketAttachmentRepository.findByTicketId(3L)).thenReturn(List.of(attachment(5L, ticket)));

            TicketDetailResponseDto result = service.getTicketById(3L, raiser);

            assertThat(result.getReplies().get(0).getAttachments()).hasSize(1);
            assertThat(result.getReplies().get(1).getAttachments()).isEmpty();
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
