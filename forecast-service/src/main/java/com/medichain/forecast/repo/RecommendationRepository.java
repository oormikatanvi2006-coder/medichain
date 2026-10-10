package com.medichain.forecast.repo;

import com.medichain.forecast.model.Recommendation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByDistrictIgnoreCaseAndStatus(String district, String status);

    List<Recommendation> findAllByOrderByCreatedAtDesc();
}
