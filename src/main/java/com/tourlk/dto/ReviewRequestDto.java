package com.tourlk.dto;

import com.tourlk.enums.ReviewableType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequestDto {

    @NotNull(message = "Reviewable type is required")
    private ReviewableType reviewableType;

    @NotNull(message = "Reviewable id is required")
    private Long reviewableId;

    @NotNull(message = "Source booking id is required")
    private Long sourceBookingId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    private String comment;

    @Size(max = 5, message = "You can attach at most 5 images")
    private List<String> imageUrls;

    public ReviewRequestDto(ReviewableType reviewableType, Long reviewableId, Long sourceBookingId,
                             Integer rating, String comment) {
        this.reviewableType = reviewableType;
        this.reviewableId = reviewableId;
        this.sourceBookingId = sourceBookingId;
        this.rating = rating;
        this.comment = comment;
    }

}
