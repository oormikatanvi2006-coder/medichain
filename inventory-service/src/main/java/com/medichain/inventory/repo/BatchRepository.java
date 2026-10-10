package com.medichain.inventory.repo;

import com.medichain.inventory.model.Batch;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByFacilityIdIn(Collection<Long> facilityIds);

    List<Batch> findByFacilityIdOrderByExpiryDateAsc(Long facilityId);

    List<Batch> findByFacilityIdAndMedicineIdOrderByExpiryDateAsc(Long facilityId, Long medicineId);
}
