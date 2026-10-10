package com.medichain.inventory.repo;

import com.medichain.inventory.model.Facility;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
    List<Facility> findByDistrictIgnoreCaseOrderByCodeAsc(String district);

    @Query("select distinct f.district from Facility f order by f.district")
    List<String> findDistricts();
}
