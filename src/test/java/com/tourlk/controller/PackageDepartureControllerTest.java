package com.tourlk.controller;

import com.tourlk.dto.PackageDepartureResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.exception.DepartureHasBookingsException;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.PackageDepartureService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link PackageDepartureController}: public listing,
 * ADMIN/GUIDE-only add/remove, request validation and the 409 on removing
 * a departure with bookings.
 */
@WebMvcTest(controllers = PackageDepartureController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class PackageDepartureControllerTest {

    private static final String FUTURE = LocalDate.now().plusDays(30).toString();
    private static final String PAST = LocalDate.now().minusDays(1).toString();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PackageDepartureService departureService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("g@example.com").role(role).build());
    }

    private PackageDepartureResponseDto sample() {
        return PackageDepartureResponseDto.builder()
                .id(7L).departureDate(LocalDate.parse(FUTURE)).seatsTotal(12).seatsLeft(9).build();
    }

    // --- GET /api/packages/{id}/departures : public ---

    @Test
    void list_noAuthentication_returnsDepartures() throws Exception {
        when(departureService.getUpcomingDepartures(10L)).thenReturn(List.of(sample()));

        mvc.perform(get("/api/packages/10/departures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].departureDate").value(FUTURE))
                .andExpect(jsonPath("$[0].seatsLeft").value(9));
    }

    // --- POST /api/packages/{id}/departures : hasAnyRole('ADMIN','GUIDE') ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void add_asGuide_returnsCreated() throws Exception {
        stubCurrentUser(Role.GUIDE);
        when(departureService.addDeparture(eq(10L), any(), any())).thenReturn(sample());

        mvc.perform(post("/api/packages/10/departures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureDate\":\"" + FUTURE + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatsTotal").value(12));
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void add_asTourist_isForbidden() throws Exception {
        mvc.perform(post("/api/packages/10/departures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureDate\":\"" + FUTURE + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(departureService);
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void add_pastDateOrZeroSeats_returnsBadRequest() throws Exception {
        mvc.perform(post("/api/packages/10/departures").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departureDate\":\"" + PAST + "\",\"seatsTotal\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.departureDate").exists())
                .andExpect(jsonPath("$.fieldErrors.seatsTotal").exists());
        verifyNoInteractions(departureService);
    }

    // --- DELETE /api/packages/{id}/departures/{departureId} : hasAnyRole('ADMIN','GUIDE') ---

    @Test
    @WithMockUser(username = "g@example.com", roles = "GUIDE")
    void delete_asGuide_returnsNoContent() throws Exception {
        stubCurrentUser(Role.GUIDE);

        mvc.perform(delete("/api/packages/10/departures/7")).andExpect(status().isNoContent());
        verify(departureService).deleteDeparture(eq(10L), eq(7L), any());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void delete_withActiveBookings_returnsConflict() throws Exception {
        stubCurrentUser(Role.ADMIN);
        doThrow(new DepartureHasBookingsException("has bookings"))
                .when(departureService).deleteDeparture(eq(10L), eq(7L), any());

        mvc.perform(delete("/api/packages/10/departures/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("has bookings"));
    }

    @Test
    @WithMockUser(roles = "TOURIST")
    void delete_asTourist_isForbidden() throws Exception {
        mvc.perform(delete("/api/packages/10/departures/7")).andExpect(status().isForbidden());
        verifyNoInteractions(departureService);
    }
}
