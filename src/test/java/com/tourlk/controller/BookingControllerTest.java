package com.tourlk.controller;

import com.tourlk.dto.BookingResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.BookingStatus;
import com.tourlk.enums.Role;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.BookingService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role-enforcement tests for {@link BookingController}
 * (@PreAuthorize allowed vs forbidden per endpoint), following the same
 * pattern as {@link PaymentControllerTest}.
 */
@WebMvcTest(controllers = BookingController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class BookingControllerTest {

    private static final String CREATE_BODY = """
            {"tourPackageId":1,"travelDate":"2030-01-01","numberOfTravelers":2}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BookingService bookingService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private BookingResponseDto sampleBooking() {
        return BookingResponseDto.builder().id(5L).touristId(1L).status(BookingStatus.PENDING).build();
    }

    // --- POST /api/bookings : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_asTourist_returnsCreated() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(bookingService.createBooking(any(), any())).thenReturn(sampleBooking());

        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void create_asGuide_isForbidden() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void create_asAdmin_isForbidden() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    // --- PUT /api/bookings/{id}/confirm : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirm_asAdmin_returnsOk() throws Exception {
        when(bookingService.confirmBooking(5L)).thenReturn(sampleBooking());

        mvc.perform(put("/api/bookings/5/confirm")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void confirm_asTourist_isForbidden() throws Exception {
        mvc.perform(put("/api/bookings/5/confirm")).andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    // --- PUT /api/bookings/{id}/cancel : hasAnyRole('TOURIST','ADMIN') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void cancel_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(bookingService.cancelBooking(anyLong(), any())).thenReturn(sampleBooking());

        mvc.perform(put("/api/bookings/5/cancel")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void cancel_asAdmin_returnsOk() throws Exception {
        stubCurrentUser(Role.ADMIN);
        when(bookingService.cancelBooking(anyLong(), any())).thenReturn(sampleBooking());

        mvc.perform(put("/api/bookings/5/cancel")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void cancel_asGuide_isForbidden() throws Exception {
        mvc.perform(put("/api/bookings/5/cancel")).andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    // --- GET /api/bookings/mine : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void mine_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(bookingService.getBookingsByTourist(1L)).thenReturn(java.util.List.of());

        mvc.perform(get("/api/bookings/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mine_asAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/bookings/mine")).andExpect(status().isForbidden());
    }

    // --- GET /api/bookings/package/{id} : hasRole('ADMIN') ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void byPackage_asAdmin_returnsOk() throws Exception {
        when(bookingService.getBookingsByPackage(9L)).thenReturn(java.util.List.of());

        mvc.perform(get("/api/bookings/package/9")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void byPackage_asTourist_isForbidden() throws Exception {
        mvc.perform(get("/api/bookings/package/9")).andExpect(status().isForbidden());
        verifyNoInteractions(bookingService);
    }

    // --- GET /api/bookings/{id} : any authenticated user ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void getById_anyAuthenticatedUser_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(bookingService.getBookingById(anyLong(), any())).thenReturn(sampleBooking());

        mvc.perform(get("/api/bookings/5")).andExpect(status().isOk());
    }
}
