package com.tourlk.controller;

import com.tourlk.dto.AuthResponseDto;
import com.tourlk.dto.UserResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.UserService;
import com.tourlk.support.MethodSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;
import com.tourlk.dto.LicenceDocumentDownload;
import org.springframework.security.access.AccessDeniedException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role-enforcement tests for {@link UserController}: /me is open to
 * any signed-in user, everything else is ADMIN-only.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    // --- /me : any authenticated user ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "GUIDE")
    void me_asAnyRole_returnsOk() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(userService.getProfile(any())).thenReturn(UserResponseDto.builder().id(1L).build());

        mvc.perform(get("/api/users/me")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void updateMe_validBody_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(userService.updateProfile(any(), any())).thenReturn(AuthResponseDto.builder().userId(1L).build());

        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tess\",\"email\":\"tess@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void updateMe_invalidEmail_isBadRequest() throws Exception {
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tess\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- admin-only ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returnsOk() throws Exception {
        when(userService.searchUsers("tess")).thenReturn(List.of());

        mvc.perform(get("/api/users").param("search", "tess")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void list_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isForbidden());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "ADMIN")
    void deactivate_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(userService.deactivateUser(any(), any())).thenReturn(UserResponseDto.builder().id(2L).build());

        mvc.perform(put("/api/users/2/deactivate")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void deactivate_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(put("/api/users/2/deactivate")).andExpect(status().isForbidden());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "ADMIN")
    void delete_asAdmin_returnsNoContent() throws Exception {
        stubCurrentUser(Role.ADMIN);

        mvc.perform(delete("/api/users/2")).andExpect(status().isNoContent());
        verify(userService).deleteUser(any(), any());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void delete_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(delete("/api/users/2")).andExpect(status().isForbidden());
        verifyNoMoreInteractions(userService);
    }

    // --- licence verification ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void submitLicence_asGuide_returnsOk() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(userService.submitLicence(any(), any(), any())).thenReturn(UserResponseDto.builder().id(1L).build());

        mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/licence")
                        .file(new MockMultipartFile("file", "dl.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("licenceNumber", "DL-1").param("licenceExpiry", "2099-01-01"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void submitLicence_asTourist_isForbidden() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/licence")
                        .file(new MockMultipartFile("file", "dl.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("licenceNumber", "DL-1").param("licenceExpiry", "2099-01-01"))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void submitLicence_withoutExpiry_isBadRequest() throws Exception {
        stubCurrentUser(Role.GUIDE);

        mvc.perform(multipart(HttpMethod.PUT, "/api/users/me/licence")
                        .file(new MockMultipartFile("file", "dl.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("licenceNumber", "DL-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void licenceDocument_forOwner_isServedInline() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(userService.getLicenceDocument(any(), any())).thenReturn(
                new LicenceDocumentDownload(new ByteArrayResource("%PDF-1.7".getBytes()), "licence.pdf", "application/pdf"));

        mvc.perform(get("/api/users/1/licence-document"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
    }

    @Test
    @WithMockUser(username = "t@example.com", roles = "TOURIST")
    void licenceDocument_forSomeoneElse_isForbidden() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(userService.getLicenceDocument(any(), any())).thenThrow(new AccessDeniedException("no"));

        mvc.perform(get("/api/users/2/licence-document")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void licenceDocument_whenNoneUploaded_isNotFound() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(userService.getLicenceDocument(any(), any()))
                .thenThrow(new com.tourlk.exception.ResourceNotFoundException("none"));

        mvc.perform(get("/api/users/2/licence-document")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendingLicences_asAdmin_returnsOk() throws Exception {
        when(userService.getPendingLicences()).thenReturn(List.of());

        mvc.perform(get("/api/users/licences/pending")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void pendingLicences_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/users/licences/pending")).andExpect(status().isForbidden());
        verifyNoMoreInteractions(userService);
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void verifyLicence_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(userService.verifyLicence(any(), any())).thenReturn(UserResponseDto.builder().id(2L).build());

        mvc.perform(put("/api/users/2/licence/verify")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void rejectLicence_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(userService.rejectLicence(any(), any(), any())).thenReturn(UserResponseDto.builder().id(2L).build());

        mvc.perform(put("/api/users/2/licence/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Blurry document\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectLicence_blankReason_isBadRequest() throws Exception {
        mvc.perform(put("/api/users/2/licence/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void deactivateMe_validPassword_returnsNoContent() throws Exception {
        when(userService.getByEmail(any())).thenReturn(
                com.tourlk.entity.User.builder().id(1L).email("u@example.com").role(com.tourlk.enums.Role.TOURIST).build());

        mvc.perform(put("/api/users/me/deactivate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isNoContent());
        verify(userService).deactivateOwnAccount(any(), org.mockito.ArgumentMatchers.eq("secret123"));
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void deactivateMe_blankPassword_returnsBadRequest() throws Exception {
        mvc.perform(put("/api/users/me/deactivate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(userService, org.mockito.Mockito.never()).deactivateOwnAccount(any(), any());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void deactivateMe_wrongPassword_returnsBadRequest() throws Exception {
        when(userService.getByEmail(any())).thenReturn(
                com.tourlk.entity.User.builder().id(1L).email("u@example.com").role(com.tourlk.enums.Role.TOURIST).build());
        org.mockito.Mockito.doThrow(new com.tourlk.exception.BadRequestException("Password is incorrect"))
                .when(userService).deactivateOwnAccount(any(), any());

        mvc.perform(put("/api/users/me/deactivate").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void deactivateById_asTourist_stillForbidden() throws Exception {
        mvc.perform(put("/api/users/5/deactivate")).andExpect(status().isForbidden());
    }
}
