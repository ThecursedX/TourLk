package com.tourlk.controller;

import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.AccommodationStatus;
import com.tourlk.enums.Role;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.AccommodationService;
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
 * Web-layer role-enforcement tests for {@link AccommodationController}.
 */
@WebMvcTest(controllers = AccommodationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class AccommodationControllerTest {

    private static final String CREATE_BODY = """
            {"name":"Ocean View","description":"Beachfront villa","locationId":1,"starRating":4}
            """;
    private static final String ROOM_BODY = """
            {"roomType":"Deluxe","pricePerNight":80.00,"totalRooms":5,"maxOccupancy":2}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AccommodationService accommodationService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private AccommodationResponseDto sample() {
        return AccommodationResponseDto.builder().id(30L).name("Ocean View")
                .status(AccommodationStatus.DRAFT).build();
    }

    // --- POST /api/accommodations : hasAnyRole('ADMIN','HOTEL_PARTNER') ---

    @Test
    @WithMockUser(username = "h@example.com", roles = "HOTEL_PARTNER")
    void create_asHotelPartner_returnsCreated() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(accommodationService.createAccommodation(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/accommodations").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void create_asTourist_isForbidden() throws Exception {
        mvc.perform(post("/api/accommodations").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accommodationService);
    }

    // --- PUT /api/accommodations/{id}/approve : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void approve_asAdmin_returnsOk() throws Exception {
        when(accommodationService.approveAccommodation(30L)).thenReturn(sample());

        mvc.perform(put("/api/accommodations/30/approve")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void approve_asHotelPartner_isForbidden() throws Exception {
        mvc.perform(put("/api/accommodations/30/approve")).andExpect(status().isForbidden());
        verifyNoInteractions(accommodationService);
    }

    // --- POST /api/accommodations/{id}/rooms : hasAnyRole('ADMIN','HOTEL_PARTNER') ---

    @Test
    @WithMockUser(username = "h@example.com", roles = "HOTEL_PARTNER")
    void addRoom_asHotelPartner_returnsCreated() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(accommodationService.addRoom(anyLong(), any(), any()))
                .thenReturn(RoomResponseDto.builder().id(40L).accommodationId(30L).roomType("Deluxe").build());

        mvc.perform(post("/api/accommodations/30/rooms").contentType(MediaType.APPLICATION_JSON).content(ROOM_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void addRoom_asTourist_isForbidden() throws Exception {
        mvc.perform(post("/api/accommodations/30/rooms").contentType(MediaType.APPLICATION_JSON).content(ROOM_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accommodationService);
    }

    // --- GET /api/accommodations : public ---

    @Test
    void browse_noAuthentication_returnsOk() throws Exception {
        when(accommodationService.getAllActive(any())).thenReturn(List.of());

        mvc.perform(get("/api/accommodations")).andExpect(status().isOk());
    }

    @Test
    void getById_noAuthentication_returnsOk() throws Exception {
        when(accommodationService.getById(30L)).thenReturn(sample());

        mvc.perform(get("/api/accommodations/30")).andExpect(status().isOk());
    }

    // --- GET /api/accommodations/pending-approval : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendingApproval_asAdmin_returnsOk() throws Exception {
        when(accommodationService.getPendingApproval()).thenReturn(List.of());

        mvc.perform(get("/api/accommodations/pending-approval")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HOTEL_PARTNER")
    void pendingApproval_asHotelPartner_isForbidden() throws Exception {
        mvc.perform(get("/api/accommodations/pending-approval")).andExpect(status().isForbidden());
    }

    // --- GET /api/accommodations/mine : hasAnyRole('ADMIN','HOTEL_PARTNER') ---

    @Test
    @WithMockUser(username = "h@example.com", roles = "HOTEL_PARTNER")
    void mine_asHotelPartner_returnsOk() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(accommodationService.getByOwner(1L)).thenReturn(List.of());

        mvc.perform(get("/api/accommodations/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void mine_asDriver_isForbidden() throws Exception {
        mvc.perform(get("/api/accommodations/mine")).andExpect(status().isForbidden());
    }
}
