package com.tourlk.controller;

import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleHireStatus;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.UserService;
import com.tourlk.service.VehicleHireService;
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
 * Web-layer role-enforcement tests for {@link VehicleHireController}.
 */
@WebMvcTest(controllers = VehicleHireController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class VehicleHireControllerTest {

    private static final String CREATE_BODY = """
            {"vehicleId":1,"startDate":"2030-01-01","endDate":"2030-01-03","pickupLocation":"CMB Airport"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VehicleHireService vehicleHireService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private VehicleHireResponseDto sample() {
        return VehicleHireResponseDto.builder().id(5L).touristId(1L).status(VehicleHireStatus.PENDING).build();
    }

    // --- POST /api/hires : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_asTourist_returnsCreated() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(vehicleHireService.createHire(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/hires").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void create_asDriver_isForbidden() throws Exception {
        mvc.perform(post("/api/hires").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vehicleHireService);
    }

    // --- PUT /api/hires/{id}/confirm : hasAnyRole('ADMIN','DRIVER') ---

    @Test
    @WithMockUser(username = "d@example.com", roles = "DRIVER")
    void confirm_asDriver_returnsOk() throws Exception {
        stubCurrentUser(Role.DRIVER);
        when(vehicleHireService.confirmHire(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/hires/5/confirm")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void confirm_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(vehicleHireService.confirmHire(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/hires/5/confirm")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void confirm_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/hires/5/confirm")).andExpect(status().isForbidden());
        verifyNoInteractions(vehicleHireService);
    }

    // --- PUT /api/hires/{id}/cancel : hasAnyRole('TOURIST','ADMIN','DRIVER') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void cancel_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(vehicleHireService.cancelHire(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/hires/5/cancel")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void cancel_asHotelPartner_isForbidden() throws Exception {
        mvc.perform(put("/api/hires/5/cancel")).andExpect(status().isForbidden());
        verifyNoInteractions(vehicleHireService);
    }

    // --- GET /api/hires/vehicle/{id} : hasAnyRole('ADMIN','DRIVER') ---

    @Test
    @WithMockUser(username = "d@example.com", roles = "DRIVER")
    void byVehicle_asDriver_returnsOk() throws Exception {
        stubCurrentUser(Role.DRIVER);
        when(vehicleHireService.getHiresByVehicle(anyLong(), any())).thenReturn(List.of());

        mvc.perform(get("/api/hires/vehicle/7")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void byVehicle_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/hires/vehicle/7")).andExpect(status().isForbidden());
        verifyNoInteractions(vehicleHireService);
    }

    // --- GET /api/hires/mine : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void mine_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(vehicleHireService.getHiresByTourist(1L)).thenReturn(List.of());

        mvc.perform(get("/api/hires/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mine_asAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/hires/mine")).andExpect(status().isForbidden());
    }
}
