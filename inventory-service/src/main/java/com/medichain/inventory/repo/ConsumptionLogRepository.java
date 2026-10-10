package com.medichain.inventory.repo;

import com.medichain.inventory.model.ConsumptionLog;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumptionLogRepository extends JpaRepository<ConsumptionLog, Long> {
    List<ConsumptionLog> findByFacilityIdInAndLogDateGreaterThanEqual(Collection<Long> facilityIds, LocalDate from);

    List<ConsumptionLog> findByFacilityIdAndMedicineIdAndLogDateGreaterThanEqualOrderByLogDateAsc(
            Long facilityId, Long medicineId, LocalDate from);

    List<ConsumptionLog> findByFacilityIdAndMedicineIdAndLogDate(Long facilityId, Long medicineId, LocalDate logDate);
}
