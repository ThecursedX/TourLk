package com.tourlk.dto;

import com.tourlk.enums.ReviewStatus;
import com.tourlk.enums.ReviewableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDto {

    private Long id;
    private ReviewableType reviewableType;
    private Long reviewableId;
    private Long sourceBookingId;
    private Long reviewerId;
    private String reviewerName;
    private int rating;
    private String comment;
    private List<String> imageUrls;
    private ReviewStatus status;
    /** Whether the reviewer can still edit this review (within the 30-day edit window). */
    private boolean editable;
    /** When the edit window closes — the reviewer's "Edit" action stops working after this instant. */
    private LocalDateTime editDeadline;
    private String guideReply;
    private LocalDateTime guideReplyAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
