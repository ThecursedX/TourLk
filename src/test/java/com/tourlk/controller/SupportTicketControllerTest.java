package com.tourlk.controller;

import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.TicketCategory;
import com.tourlk.enums.TicketStatus;
import com.tourlk.dto.TicketAttachmentDownload;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.TicketAccessDeniedException;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.SupportTicketService;
import com.tourlk.service.UserService;
import com.tourlk.support.MethodSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role-enforcement tests for {@link SupportTicketController}. Most
 * endpoints allow any authenticated user (privacy is enforced in the
 * service); only the triage endpoints are ADMIN-only.
 */
@WebMvcTest(controllers = SupportTicketController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class SupportTicketControllerTest {

    private static final String TICKET_BODY = """
            {"subject":"Cannot pay","category":"PAYMENT","message":"My card keeps failing"}
            """;
    private static final String REPLY_BODY = """
            {"message":"Any update on this?"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SupportTicketService supportTicketService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private SupportTicketResponseDto sampleTicket() {
        return SupportTicketResponseDto.builder().id(3L).subject("Cannot pay")
                .category(TicketCategory.PAYMENT).status(TicketStatus.OPEN).build();
    }

    // --- POST /api/tickets : any authenticated user ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_asTourist_returnsCreated() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.createTicket(any(), any())).thenReturn(sampleTicket());

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(TICKET_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "d@example.com", roles = "DRIVER")
    void create_asDriver_returnsCreated() throws Exception {
        stubCurrentUser(Role.DRIVER);
        when(supportTicketService.createTicket(any(), any())).thenReturn(sampleTicket());

        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(TICKET_BODY))
                .andExpect(status().isCreated());
    }

    // --- POST /api/tickets/{id}/replies : any authenticated user ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "HOTEL_PARTNER")
    void addReply_asAnyRole_returnsCreated() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(supportTicketService.addReply(anyLong(), any(), any()))
                .thenReturn(TicketReplyResponseDto.builder().id(11L).message("Any update on this?").build());

        mvc.perform(post("/api/tickets/3/replies").contentType(MediaType.APPLICATION_JSON).content(REPLY_BODY))
                .andExpect(status().isCreated());
    }

    // --- PUT /api/tickets/{id}/assign : hasRole('ADMIN') ---

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void assign_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(supportTicketService.assignTicket(anyLong(), anyLong())).thenReturn(sampleTicket());

        mvc.perform(put("/api/tickets/3/assign")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void assign_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/tickets/3/assign")).andExpect(status().isForbidden());
        verifyNoInteractions(supportTicketService);
    }

    // --- PUT /api/tickets/{id}/resolve : raiser or admin (checked in the service) ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "ADMIN")
    void resolve_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(supportTicketService.resolveTicket(eq(3L), any())).thenReturn(sampleTicket());

        mvc.perform(put("/api/tickets/3/resolve")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void resolve_asTourist_isDelegatedToTheServiceWhichEnforcesOwnership() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.resolveTicket(eq(3L), any()))
                .thenThrow(new TicketAccessDeniedException("not yours"));

        mvc.perform(put("/api/tickets/3/resolve")).andExpect(status().isForbidden());
        verify(supportTicketService).resolveTicket(eq(3L), any());
    }

    // --- PUT /api/tickets/{id}/withdraw : any authenticated user (raiser enforced in the service) ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void withdraw_asRaiser_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.withdrawTicket(eq(3L), any())).thenReturn(sampleTicket());

        mvc.perform(put("/api/tickets/3/withdraw")).andExpect(status().isOk());
    }

    // --- multipart create / reply with attachments ---

    private static MockMultipartFile json(String content) {
        return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, content.getBytes());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_multipartWithFiles_returnsCreatedAndPassesFiles() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.createTicket(any(), any(), any())).thenReturn(sampleTicket());
        MockMultipartFile file = new MockMultipartFile("files", "shot.png", "image/png", new byte[] {1, 2, 3});

        mvc.perform(multipart("/api/tickets").file(json(TICKET_BODY)).file(file))
                .andExpect(status().isCreated());
        verify(supportTicketService).createTicket(any(), argThat(files -> files.size() == 1), any());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_multipartWithoutFiles_isAllowed() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.createTicket(any(), any(), any())).thenReturn(sampleTicket());

        mvc.perform(multipart("/api/tickets").file(json(TICKET_BODY))).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_multipartInvalidData_returnsBadRequest() throws Exception {
        stubCurrentUser(Role.TOURIST);

        mvc.perform(multipart("/api/tickets").file(json("{\"subject\":\"\"}"))).andExpect(status().isBadRequest());
        verifyNoInteractions(supportTicketService);
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_serviceRejectsFile_returnsBadRequest() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.createTicket(any(), any(), any())).thenThrow(new BadRequestException("not allowed"));
        MockMultipartFile file = new MockMultipartFile("files", "run.exe", "application/x-msdownload", new byte[] {1});

        mvc.perform(multipart("/api/tickets").file(json(TICKET_BODY)).file(file)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void addReply_multipartWithFiles_returnsCreated() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.addReply(eq(3L), any(), any(), any()))
                .thenReturn(TicketReplyResponseDto.builder().id(1L).build());
        MockMultipartFile file = new MockMultipartFile("files", "doc.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mvc.perform(multipart("/api/tickets/3/replies").file(json(REPLY_BODY)).file(file))
                .andExpect(status().isCreated());
    }

    // --- GET /api/tickets/{id}/attachments/{attachmentId} ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void downloadAttachment_returnsFileAsAttachmentWithNosniff() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.getAttachment(eq(3L), eq(5L), any())).thenReturn(new TicketAttachmentDownload(
                new ByteArrayResource(new byte[] {1, 2, 3}), "shot.png", "image/png", 3));

        mvc.perform(get("/api/tickets/3/attachments/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void downloadAttachment_notTheRaiser_isForbidden() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.getAttachment(eq(3L), eq(5L), any()))
                .thenThrow(new TicketAccessDeniedException("not yours"));

        mvc.perform(get("/api/tickets/3/attachments/5")).andExpect(status().isForbidden());
    }

    // --- PUT /api/tickets/{id}/reopen : any authenticated user ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void reopen_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.reopenTicket(anyLong(), any())).thenReturn(sampleTicket());

        mvc.perform(put("/api/tickets/3/reopen")).andExpect(status().isOk());
    }

    // --- GET /api/tickets : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAll_asAdmin_returnsOk() throws Exception {
        when(supportTicketService.getAllTickets(any())).thenReturn(List.of());

        mvc.perform(get("/api/tickets")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void listAll_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isForbidden());
        verifyNoInteractions(supportTicketService);
    }

    // --- GET /api/tickets/unassigned : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "TOURIST")
    void unassigned_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/tickets/unassigned")).andExpect(status().isForbidden());
        verifyNoInteractions(supportTicketService);
    }

    // --- GET /api/tickets/mine and /{id} : any authenticated user ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void mine_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.getMyTickets(1L)).thenReturn(List.of());

        mvc.perform(get("/api/tickets/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void getById_asAnyAuthenticatedUser_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(supportTicketService.getTicketById(anyLong(), any()))
                .thenReturn(TicketDetailResponseDto.builder().ticket(sampleTicket()).replies(List.of()).build());

        mvc.perform(get("/api/tickets/3")).andExpect(status().isOk());
    }
}
