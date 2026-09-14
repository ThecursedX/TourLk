package com.tourlk.controller;

import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.User;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role-enforcement tests for {@link RoomController} (flat
 * /api/rooms/{id} mutations; both endpoints are ADMIN/HOTEL_PARTNER only).
 */
@WebMvcTest(controllers = RoomController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class RoomControllerTest {

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

    @Test
    @WithMockUser(username = "h@example.com", roles = "HOTEL_PARTNER")
    void update_asHotelPartner_returnsOk() throws Exception {
        stubCurrentUser(Role.HOTEL_PARTNER);
        when(accommodationService.updateRoom(anyLong(), any(), any()))
                .thenReturn(RoomResponseDto.builder().id(40L).roomType("Deluxe").build());

        mvc.perform(put("/api/rooms/40").contentType(MediaType.APPLICATION_JSON).content(ROOM_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void update_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/rooms/40").contentType(MediaType.APPLICATION_JSON).content(ROOM_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accommodationService);
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void remove_asAdmin_returnsNoContent() throws Exception {
        stubCurrentUser(Role.ADMIN);
        doNothing().when(accommodationService).removeRoom(anyLong(), any());

        mvc.perform(delete("/api/rooms/40")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void remove_asDriver_isForbidden() throws Exception {
        mvc.perform(delete("/api/rooms/40")).andExpect(status().isForbidden());
        verifyNoInteractions(accommodationService);
    }
}
