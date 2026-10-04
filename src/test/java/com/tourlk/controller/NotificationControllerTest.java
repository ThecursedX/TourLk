package com.tourlk.controller;

import com.tourlk.dto.NotificationResponseDto;
import com.tourlk.dto.UnreadCountResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.NotificationService;
import com.tourlk.service.UserService;
import com.tourlk.support.MethodSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link NotificationController}. Every endpoint only
 * requires an authenticated user of any role — recipient-only access is
 * enforced in {@code NotificationServiceImpl}, not here.
 */
@WebMvcTest(controllers = NotificationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NotificationService notificationService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser() {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(Role.TOURIST).build());
    }

    private NotificationResponseDto sampleNotification() {
        return NotificationResponseDto.builder()
                .id(3L).type(NotificationType.BOOKING_CONFIRMED)
                .title("Booking confirmed").message("Your booking is confirmed")
                .linkUrl("/bookings/5").read(false).build();
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void mine_asAnyAuthenticatedUser_returnsOk() throws Exception {
        stubCurrentUser();
        when(notificationService.getMyNotifications(any())).thenReturn(List.of(sampleNotification()));

        mvc.perform(get("/api/notifications/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "GUIDE")
    void unreadCount_asAnyAuthenticatedUser_returnsOk() throws Exception {
        stubCurrentUser();
        when(notificationService.getUnreadCount(any())).thenReturn(4L);

        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "ADMIN")
    void markRead_asAnyAuthenticatedUser_returnsOk() throws Exception {
        stubCurrentUser();
        NotificationResponseDto read = NotificationResponseDto.builder().id(3L).read(true).build();
        when(notificationService.markRead(3L, userService.getByEmail("u@example.com"))).thenReturn(read);

        mvc.perform(put("/api/notifications/3/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser(username = "u@example.com", roles = "DRIVER")
    void markAllRead_asAnyAuthenticatedUser_returnsNoContent() throws Exception {
        stubCurrentUser();

        mvc.perform(put("/api/notifications/read-all")).andExpect(status().isNoContent());
    }
}
