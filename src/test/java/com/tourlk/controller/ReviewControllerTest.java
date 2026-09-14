package com.tourlk.controller;

import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.Role;
import com.tourlk.enums.ReviewableType;
import com.tourlk.security.JwtFilter;
import com.tourlk.service.ReviewService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role-enforcement tests for {@link ReviewController}, including
 * the public browse / summary endpoints that carry no {@code @PreAuthorize}.
 */
@WebMvcTest(controllers = ReviewController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE, classes = JwtFilter.class))
@Import(MethodSecurityTestConfig.class)
class ReviewControllerTest {

    private static final String REVIEW_BODY = """
            {"reviewableType":"TOUR_PACKAGE","reviewableId":1,"sourceBookingId":10,"rating":5,"comment":"Great"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReviewService reviewService;
    @MockBean
    private UserService userService;

    private void stubCurrentUser(Role role) {
        when(userService.getByEmail(any()))
                .thenReturn(User.builder().id(1L).email("u@example.com").role(role).build());
    }

    private ReviewResponseDto sample() {
        return ReviewResponseDto.builder().id(5L).reviewerId(1L)
                .reviewableType(ReviewableType.TOUR_PACKAGE).reviewableId(1L).rating(5).build();
    }

    // --- POST /api/reviews : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void create_asTourist_returnsCreated() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(reviewService.createReview(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_isForbidden() throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);
    }

    // --- PUT /api/reviews/{id} : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void update_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(reviewService.updateReview(anyLong(), any(), any())).thenReturn(sample());

        mvc.perform(put("/api/reviews/5").contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_isForbidden() throws Exception {
        mvc.perform(put("/api/reviews/5").contentType(MediaType.APPLICATION_JSON).content(REVIEW_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);
    }

    // --- DELETE /api/reviews/{id} : hasAnyRole('TOURIST','ADMIN') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void delete_asTourist_returnsNoContent() throws Exception {
        stubCurrentUser(Role.TOURIST);
        doNothing().when(reviewService).deleteReview(anyLong(), any());

        mvc.perform(delete("/api/reviews/5")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "a@example.com", roles = "ADMIN")
    void delete_asAdmin_returnsNoContent() throws Exception {
        stubCurrentUser(Role.ADMIN);
        doNothing().when(reviewService).deleteReview(anyLong(), any());

        mvc.perform(delete("/api/reviews/5")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void delete_asGuide_isForbidden() throws Exception {
        mvc.perform(delete("/api/reviews/5")).andExpect(status().isForbidden());
        verifyNoInteractions(reviewService);
    }

    // --- GET /api/reviews : public (no @PreAuthorize) ---

    @Test
    @WithMockUser(roles = "TOURIST")
    void browse_anyAuthenticatedUser_returnsOk() throws Exception {
        when(reviewService.getReviewsFor(eq(ReviewableType.TOUR_PACKAGE), eq(1L))).thenReturn(List.of());

        mvc.perform(get("/api/reviews").param("type", "TOUR_PACKAGE").param("id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GUIDE")
    void summary_anyAuthenticatedUser_returnsOk() throws Exception {
        when(reviewService.getRatingSummary(eq(ReviewableType.ACCOMMODATION), eq(2L)))
                .thenReturn(RatingSummaryDto.builder().reviewableType(ReviewableType.ACCOMMODATION)
                        .reviewableId(2L).averageRating(4.5).totalReviews(2).build());

        mvc.perform(get("/api/reviews/summary").param("type", "ACCOMMODATION").param("id", "2"))
                .andExpect(status().isOk());
    }

    // --- GET /api/reviews/mine : hasRole('TOURIST') ---

    @Test
    @WithMockUser(username = "u@example.com", roles = "TOURIST")
    void mine_asTourist_returnsOk() throws Exception {
        stubCurrentUser(Role.TOURIST);
        when(reviewService.getReviewsByUser(1L)).thenReturn(List.of());

        mvc.perform(get("/api/reviews/mine")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void mine_asAdmin_isForbidden() throws Exception {
        mvc.perform(get("/api/reviews/mine")).andExpect(status().isForbidden());
    }
}
