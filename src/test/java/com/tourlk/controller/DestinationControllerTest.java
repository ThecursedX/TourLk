package com.tourlk.controller;

import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.enums.DestinationStatus;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
            {"name":"Ella","region":"Uva Province","description":"Tea country"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DestinationService destinationService;

    private DestinationResponseDto sample() {
        return DestinationResponseDto.builder().id(1L).name("Ella").region("Uva Province")
                .status(DestinationStatus.ACTIVE).build();
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
        when(destinationService.getById(1L)).thenReturn(sample());

        mvc.perform(get("/api/destinations/1")).andExpect(status().isOk());
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
