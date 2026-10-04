package com.tourlk.repo;

import com.tourlk.entity.ReviewEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewEditHistoryRepository extends JpaRepository<ReviewEditHistory, Long> {

    List<ReviewEditHistory> findByReviewIdOrderByCreatedAtDesc(Long reviewId);

    boolean existsByReviewId(Long reviewId);

}
