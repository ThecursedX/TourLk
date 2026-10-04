package com.tourlk.controller;

import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.Province;
import com.tourlk.exception.BadRequestException;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.DestinationService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
 * Web-layer role-enforcement tests for {@link DestinationController}:
 * writes + the "all" listing are ADMIN-only; browse + get-by-id are public.
 */
@WebMvcTest(controllers = DestinationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class DestinationControllerTest {

    private static final String CREATE_BODY = """
            {"name":"Ella","description":"Tea country","province":"UVA","district":"Badulla","category":"Nature"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DestinationService destinationService;

    private DestinationResponseDto sample() {
        return DestinationResponseDto.builder().id(1L).name("Ella").region("Uva Province")
                .status(DestinationStatus.PUBLISHED).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returnsCreated() throws Exception {
        when(destinationService.createDestination(any())).thenReturn(sample());

        mvc.perform(post("/api/destinations").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void create_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(post("/api/destinations").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivate_asAdmin_returnsOk() throws Exception {
        when(destinationService.deactivateDestination(1L)).thenReturn(sample());

        mvc.perform(put("/api/destinations/1/deactivate")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void deactivate_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(put("/api/destinations/1/deactivate")).andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }

    @Test
    void browse_noAuthentication_returnsOk() throws Exception {
        when(destinationService.getAllActive()).thenReturn(List.of());

        mvc.perform(get("/api/destinations")).andExpect(status().isOk());
    }

    @Test
    void getById_noAuthentication_returnsOk() throws Exception {
        when(destinationService.getById(eq(1L), anyBoolean())).thenReturn(sample());

        mvc.perform(get("/api/destinations/1")).andExpect(status().isOk());
    }

    @Test
    void browse_nearby_usesNearbySearch() throws Exception {
        when(destinationService.searchNearby("6.9,79.8", 25.0)).thenReturn(List.of());

        mvc.perform(get("/api/destinations").param("nearby", "6.9,79.8").param("radiusKm", "25"))
                .andExpect(status().isOk());
        verify(destinationService).searchNearby("6.9,79.8", 25.0);
        verify(destinationService, never()).getAllActive();
    }

    @Test
    void browse_nearbyMalformed_returnsBadRequest() throws Exception {
        when(destinationService.searchNearby(any(), any())).thenThrow(new BadRequestException("bad nearby"));

        mvc.perform(get("/api/destinations").param("nearby", "oops")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_asAdmin_passesAdminFlag() throws Exception {
        when(destinationService.getById(eq(1L), eq(true))).thenReturn(sample());

        mvc.perform(get("/api/destinations/1")).andExpect(status().isOk());
        verify(destinationService).getById(1L, true);
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void getById_asTourist_passesNonAdminFlag() throws Exception {
        when(destinationService.getById(eq(1L), eq(false))).thenThrow(new ResourceNotFoundException("gone"));

        mvc.perform(get("/api/destinations/1")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void close_asAdmin_returnsOk() throws Exception {
        when(destinationService.closeTemporarily(eq(1L), eq("Repairs"), any(), any())).thenReturn(sample());

        mvc.perform(put("/api/destinations/1/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Repairs\",\"until\":\"2099-01-31\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void closureImpact_asAdmin_returnsCount() throws Exception {
        when(destinationService.previewClosureImpact(eq(1L), any(), any()))
                .thenReturn(new com.tourlk.dto.ClosureImpactDto(3));

        mvc.perform(get("/api/destinations/1/closure-impact?from=2099-01-01&until=2099-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedBookings").value(3));
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void closureImpact_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/destinations/1/closure-impact")).andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void close_withoutReason_returnsBadRequest() throws Exception {
        mvc.perform(put("/api/destinations/1/close").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(destinationService);
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void close_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/destinations/1/close").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Repairs\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void archive_asAdmin_returnsOk() throws Exception {
        when(destinationService.archiveDestination(1L)).thenReturn(sample());

        mvc.perform(delete("/api/destinations/1")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void archive_asGuide_isForbidden() throws Exception {
        mvc.perform(delete("/api/destinations/1")).andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }

    @Test
    void categories_noAuthentication_returnsOk() throws Exception {
        when(destinationService.getCategorySuggestions()).thenReturn(List.of("Beach"));

        mvc.perform(get("/api/destinations/categories")).andExpect(status().isOk());
    }

    @Test
    void browse_byProvince_returnsOk() throws Exception {
        when(destinationService.getByProvince(Province.UVA)).thenReturn(List.of());

        mvc.perform(get("/api/destinations").param("province", "UVA")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_missingProvince_isBadRequest() throws Exception {
        String body = """
                {"name":"Ella","district":"Badulla","category":"Nature"}
                """;

        mvc.perform(post("/api/destinations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(destinationService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_asAdmin_returnsOk() throws Exception {
        when(destinationService.getAll()).thenReturn(List.of());

        mvc.perform(get("/api/destinations/all")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void getAll_asNonAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/destinations/all")).andExpect(status().isForbidden());
        verifyNoInteractions(destinationService);
    }
}
