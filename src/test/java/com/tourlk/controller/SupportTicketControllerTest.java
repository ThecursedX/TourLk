package com.tourlk.controller;

import com.tourlk.dto.SupportTicketResponseDto;
import com.tourlk.dto.TicketDetailResponseDto;
import com.tourlk.dto.TicketReplyResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.TicketCategory;
import com.tourlk.enums.TicketStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // --- PUT /api/tickets/{id}/resolve : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void resolve_asAdmin_returnsOk() throws Exception {
        when(supportTicketService.resolveTicket(3L)).thenReturn(sampleTicket());

        mvc.perform(put("/api/tickets/3/resolve")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void resolve_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/tickets/3/resolve")).andExpect(status().isForbidden());
        verifyNoInteractions(supportTicketService);
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
