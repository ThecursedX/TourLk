package com.tourlk.controller;

import com.tourlk.dto.PackageSearchCriteria;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageSort;
import com.tourlk.enums.PackageStatus;
import com.tourlk.enums.Role;
import com.tourlk.exception.ChangesRequireConfirmationException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.TourPackageService;
import com.tourlk.service.UserService;
import com.tourlk.support.MethodSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @MockBean
    private com.tourlk.service.PackageAddOnService packageAddOnService;

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
    void browse_noAuthentication_returnsOkWithRatingFields() throws Exception {
        TourPackageResponseDto rated = TourPackageResponseDto.builder()
                .id(10L).title("Ella Trek").status(PackageStatus.ACTIVE)
                .averageRating(4.5).reviewCount(8).budgetTier(BudgetTier.STANDARD).build();
        when(tourPackageService.browsePackages(any())).thenReturn(List.of(rated));

        mvc.perform(get("/api/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].averageRating").value(4.5))
                .andExpect(jsonPath("$[0].reviewCount").value(8))
                .andExpect(jsonPath("$[0].budgetTier").value("STANDARD"));
    }

    @Test
    void browse_allParams_areBoundIntoCriteria() throws Exception {
        when(tourPackageService.browsePackages(any())).thenReturn(List.of());

        mvc.perform(get("/api/packages")
                        .param("destinationId", "1").param("minPrice", "50").param("maxPrice", "500")
                        .param("q", "tea").param("minDays", "2").param("maxDays", "5")
                        .param("budgetTier", "LUXURY").param("travelDate", "2026-12-01").param("sort", "price_desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<PackageSearchCriteria> captor = ArgumentCaptor.forClass(PackageSearchCriteria.class);
        verify(tourPackageService).browsePackages(captor.capture());
        PackageSearchCriteria criteria = captor.getValue();
        assertThat(criteria.getDestinationId()).isEqualTo(1L);
        assertThat(criteria.getMinPrice()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(criteria.getMaxPrice()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(criteria.getQ()).isEqualTo("tea");
        assertThat(criteria.getMinDays()).isEqualTo(2);
        assertThat(criteria.getMaxDays()).isEqualTo(5);
        assertThat(criteria.getBudgetTier()).isEqualTo(BudgetTier.LUXURY);
        assertThat(criteria.getTravelDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(criteria.getSort()).isEqualTo(PackageSort.PRICE_DESC);
    }

    @Test
    void browse_unknownSort_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/packages").param("sort", "cheapest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("price_asc")));
        verifyNoInteractions(tourPackageService);
    }

    @Test
    void browse_unknownBudgetTierOrBadDate_returnsBadRequest() throws Exception {
        mvc.perform(get("/api/packages").param("budgetTier", "CHEAP")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/packages").param("travelDate", "01/12/2026")).andExpect(status().isBadRequest());
        verifyNoInteractions(tourPackageService);
    }

    @Test
    void getById_noAuthentication_returnsOk() throws Exception {
        when(tourPackageService.getPackageById(10L, null)).thenReturn(sample());

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

    // --- GET /api/packages/{id} : public, but passes the caller through for visibility checks ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void getById_authenticated_passesCurrentUserToService() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.getPackageById(eq(10L), any())).thenReturn(sample());

        mvc.perform(get("/api/packages/10")).andExpect(status().isOk());

        verify(tourPackageService).getPackageById(eq(10L), argThat(
                user -> user != null && user.getRole() == Role.GUIDE));
    }

    @Test
    void getById_hiddenPackage_returns404() throws Exception {
        when(tourPackageService.getPackageById(eq(10L), isNull()))
                .thenThrow(new ResourceNotFoundException("Tour package not found with id: 10"));

        mvc.perform(get("/api/packages/10")).andExpect(status().isNotFound());
    }

    // --- GET /api/packages/admin : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminList_asAdmin_filtersByStatus() throws Exception {
        when(tourPackageService.getAllPackagesForAdmin(PackageStatus.DRAFT)).thenReturn(List.of(sample()));

        mvc.perform(get("/api/packages/admin").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminList_noStatus_passesNull() throws Exception {
        when(tourPackageService.getAllPackagesForAdmin(null)).thenReturn(List.of());

        mvc.perform(get("/api/packages/admin")).andExpect(status().isOk());
        verify(tourPackageService).getAllPackagesForAdmin(null);
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void adminList_asGuide_isForbidden() throws Exception {
        mvc.perform(get("/api/packages/admin")).andExpect(status().isForbidden());
        verifyNoInteractions(tourPackageService);
    }

    @Test
    void adminList_anonymous_isForbidden() throws Exception {
        mvc.perform(get("/api/packages/admin")).andExpect(status().isForbidden());
        verifyNoInteractions(tourPackageService);
    }

    // --- PUT /api/packages/{id}/reject : hasRole('ADMIN'), reason required ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void reject_withReason_returnsOk() throws Exception {
        when(tourPackageService.rejectPackage(10L, "Add photos")).thenReturn(sample());

        mvc.perform(put("/api/packages/10/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Add photos\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reject_blankOrMissingReason_returnsBadRequest() throws Exception {
        mvc.perform(put("/api/packages/10/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put("/api/packages/10/reject")).andExpect(status().isBadRequest());
        verifyNoInteractions(tourPackageService);
    }

    // --- PUT /api/packages/{id} : confirmChanges ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void update_withoutConfirmation_needingIt_returns409WithDetails() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.updatePackage(eq(10L), any(), any(), eq(false)))
                .thenThrow(new ChangesRequireConfirmationException(List.of("price"), 4));

        mvc.perform(put("/api/packages/10").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.details.affectedBookings").value(4));
    }

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void update_confirmChangesParam_isPassedThrough() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(tourPackageService.updatePackage(eq(10L), any(), any(), eq(true))).thenReturn(sample());

        mvc.perform(put("/api/packages/10").param("confirmChanges", "true")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());
    }
}
