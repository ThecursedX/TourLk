package com.tourlk.controller;

import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.TourPackageService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role-enforcement tests for {@link TourPackageController}.
 */
@WebMvcTest(controllers = TourPackageController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class TourPackageControllerTest {

    private static final String CREATE_BODY = """
            {"title":"Ella Trek","description":"Nine Arch Bridge and tea trails","destinationId":1,
             "durationDays":3,"price":150.00,"maxCapacity":12}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TourPackageService tourPackageService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private TourPackageResponseDto sample() {
        return TourPackageResponseDto.builder().id(10L).title("Ella Trek").status(PackageStatus.DRAFT).build();
    }

    // --- POST /api/packages : hasAnyRole('ADMIN','GUIDE') ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void create_asGuide_returnsCreated() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.createPackage(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/packages").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void create_asTourist_isForbidden() throws Exception {
        mvc.perform(post("/api/packages").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tourPackageService);
    }

    // --- PUT /api/packages/{id}/submit : hasAnyRole('ADMIN','GUIDE') ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void submit_asGuide_returnsOk() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.submitForApproval(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/packages/10/submit")).andExpect(status().isOk());
    }

    // --- PUT /api/packages/{id}/approve : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void approve_asAdmin_returnsOk() throws Exception {
        when(tourPackageService.approvePackage(10L)).thenReturn(sample());

        mvc.perform(put("/api/packages/10/approve")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void approve_asGuide_isForbidden() throws Exception {
        mvc.perform(put("/api/packages/10/approve")).andExpect(status().isForbidden());
        verifyNoInteractions(tourPackageService);
    }

    // --- DELETE /api/packages/{id} (archive) : hasAnyRole('ADMIN','GUIDE') ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void archive_asGuide_returnsOk() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.archivePackage(anyLong(), any())).thenReturn(sample());

        mvc.perform(delete("/api/packages/10")).andExpect(status().isOk());
    }

    // --- GET /api/packages : public ---

    @Test
    void browse_noAuthentication_returnsOk() throws Exception {
        when(tourPackageService.getAllActivePackages()).thenReturn(List.of());

        mvc.perform(get("/api/packages")).andExpect(status().isOk());
    }

    @Test
    void browse_withFilters_delegatesToSearch() throws Exception {
        when(tourPackageService.searchPackages(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/packages").param("destinationId", "1")).andExpect(status().isOk());
    }

    @Test
    void getById_noAuthentication_returnsOk() throws Exception {
        when(tourPackageService.getPackageById(10L)).thenReturn(sample());

        mvc.perform(get("/api/packages/10")).andExpect(status().isOk());
    }

    // --- GET /api/packages/pending-approval : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendingApproval_asAdmin_returnsOk() throws Exception {
        when(tourPackageService.getPendingApprovalPackages()).thenReturn(List.of());

        mvc.perform(get("/api/packages/pending-approval")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void pendingApproval_asGuide_isForbidden() throws Exception {
        mvc.perform(get("/api/packages/pending-approval")).andExpect(status().isForbidden());
        verifyNoInteractions(tourPackageService);
    }

    // --- GET /api/packages/mine : hasAnyRole('ADMIN','GUIDE') ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void mine_asGuide_returnsOk() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.getPackagesByCreator(1L)).thenReturn(List.of());

        mvc.perform(get("/api/packages/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void mine_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/packages/mine")).andExpect(status().isForbidden());
    }
}
