package com.tourlk.controller;

import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.UserService;
import com.tourlk.service.VehicleService;
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
 * Web-layer role-enforcement tests for {@link VehicleController}.
 */
@WebMvcTest(controllers = VehicleController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class VehicleControllerTest {

    private static final String CREATE_BODY = """
            {"vehicleType":"VAN","make":"Toyota","model":"HiAce","registrationNumber":"NA-1234",
             "seatingCapacity":9,"pricePerDay":90.00}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private VehicleService vehicleService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private VehicleResponseDto sample() {
        return VehicleResponseDto.builder().id(20L).vehicleType(VehicleType.VAN)
                .status(VehicleStatus.PENDING_APPROVAL).build();
    }

    // --- POST /api/vehicles : hasRole('DRIVER') ---

    @Test
    @WithMockUser(username = "d@example.com", roles = "DRIVER")
    void create_asDriver_returnsCreated() throws Exception {
        stubCurrentUser(Role.DRIVER);
        when(vehicleService.createVehicle(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_isForbidden() throws Exception {
        mvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vehicleService);
    }

    // --- PUT /api/vehicles/{id} : hasAnyRole('ADMIN','DRIVER') ---

    @Test
    @WithMockUser(username = "d@example.com", roles = "DRIVER")
    void update_asDriver_returnsOk() throws Exception {
        stubCurrentUser(Role.DRIVER);
        when(vehicleService.updateVehicle(anyLong(), any(), any())).thenReturn(sample());

        mvc.perform(put("/api/vehicles/20").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void update_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/vehicles/20").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vehicleService);
    }

    // --- PUT /api/vehicles/{id}/approve : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void approve_asAdmin_returnsOk() throws Exception {
        when(vehicleService.approveVehicle(20L)).thenReturn(sample());

        mvc.perform(put("/api/vehicles/20/approve")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void approve_asDriver_isForbidden() throws Exception {
        mvc.perform(put("/api/vehicles/20/approve")).andExpect(status().isForbidden());
        verifyNoInteractions(vehicleService);
    }

    // --- GET /api/vehicles : public ---

    @Test
    void browse_noAuthentication_returnsOk() throws Exception {
        when(vehicleService.getAllActive(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/vehicles")).andExpect(status().isOk());
    }

    @Test
    void getById_noAuthentication_returnsOk() throws Exception {
        when(vehicleService.getById(20L)).thenReturn(sample());

        mvc.perform(get("/api/vehicles/20")).andExpect(status().isOk());
    }

    // --- GET /api/vehicles/mine : hasRole('DRIVER') ---

    @Test
    @WithMockUser(username = "d@example.com", roles = "DRIVER")
    void mine_asDriver_returnsOk() throws Exception {
        stubCurrentUser(Role.DRIVER);
        when(vehicleService.getByDriver(1L)).thenReturn(List.of());

        mvc.perform(get("/api/vehicles/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mine_asAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/vehicles/mine")).andExpect(status().isForbidden());
    }

    // --- GET /api/vehicles/pending-approval : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendingApproval_asAdmin_returnsOk() throws Exception {
        when(vehicleService.getPendingApproval()).thenReturn(List.of());

        mvc.perform(get("/api/vehicles/pending-approval")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void pendingApproval_asDriver_isForbidden() throws Exception {
        mvc.perform(get("/api/vehicles/pending-approval")).andExpect(status().isForbidden());
    }
}
