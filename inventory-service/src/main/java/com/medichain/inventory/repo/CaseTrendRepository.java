package com.medichain.inventory.repo;

import com.medichain.inventory.model.CaseTrend;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseTrendRepository extends JpaRepository<CaseTrend, Long> {
    List<CaseTrend> findByDistrictIgnoreCaseAndReportDateGreaterThanEqual(String district, LocalDate from);
}
