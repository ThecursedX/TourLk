package com.tourlk.controller;

import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.RoomReservationStatus;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.RoomReservationService;
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
 * Web-layer role-enforcement tests for {@link RoomReservationController}.
 */
@WebMvcTest(controllers = RoomReservationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class RoomReservationControllerTest {

    private static final String CREATE_BODY = """
            {"roomId":1,"checkInDate":"2030-01-01","checkOutDate":"2030-01-04","numberOfRooms":1}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RoomReservationService roomReservationService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private RoomReservationResponseDto sample() {
        return RoomReservationResponseDto.builder().id(5L).touristId(1L)
                .status(RoomReservationStatus.PENDING).build();
    }

    // --- POST /api/reservations : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_asTourist_returnsCreated() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(roomReservationService.createReservation(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void create_asHotelPartner_isForbidden() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(roomReservationService);
    }

    // --- PUT /api/reservations/{id}/confirm : hasAnyRole('ADMIN','HOTEL_PARTNER') ---

    @Test
    @WithMockUser(username = "h@example.com", roles = "HOTEL_PARTNER")
    void confirm_asHotelPartner_returnsOk() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(roomReservationService.confirmReservation(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/reservations/5/confirm")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void confirm_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(roomReservationService.confirmReservation(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/reservations/5/confirm")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void confirm_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/reservations/5/confirm")).andExpect(status().isForbidden());
        verifyNoInteractions(roomReservationService);
    }

    // --- PUT /api/reservations/{id}/cancel : hasAnyRole('TOURIST','ADMIN','HOTEL_PARTNER') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void cancel_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(roomReservationService.cancelReservation(anyLong(), any())).thenReturn(sample());

        mvc.perform(put("/api/reservations/5/cancel")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void cancel_asDriver_isForbidden() throws Exception {
        mvc.perform(put("/api/reservations/5/cancel")).andExpect(status().isForbidden());
        verifyNoInteractions(roomReservationService);
    }

    // --- GET /api/reservations/room/{id} : hasAnyRole('ADMIN','HOTEL_PARTNER') ---

    @Test
    @WithMockUser(username = "h@example.com", roles = "HOTEL_PARTNER")
    void byRoom_asHotelPartner_returnsOk() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(roomReservationService.getReservationsByRoom(anyLong(), any())).thenReturn(List.of());

        mvc.perform(get("/api/reservations/room/3")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void byRoom_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/reservations/room/3")).andExpect(status().isForbidden());
        verifyNoInteractions(roomReservationService);
    }

    // --- GET /api/reservations/mine : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void mine_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(roomReservationService.getReservationsByTourist(1L)).thenReturn(List.of());

        mvc.perform(get("/api/reservations/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void mine_asHotelPartner_isForbidden() throws Exception {
        mvc.perform(get("/api/reservations/mine")).andExpect(status().isForbidden());
    }
}
