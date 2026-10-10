package com.medichain.inventory.repo;

import com.medichain.inventory.model.StockTransaction;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findByFacilityIdInAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Collection<Long> facilityIds, LocalDateTime from);
}
