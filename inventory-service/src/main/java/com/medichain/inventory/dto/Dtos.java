package com.medichain.inventory.dto;

import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes of the inventory REST API. */
public final class Dtos {
    private Dtos() {
    }

    public record BatchDto(Long id, String batchNo, int quantity, LocalDate manufactureDate, LocalDate expiryDate) {
    }

    /**
     * Everything known about one medicine at one facility. The daily lists run oldest to newest and
     * end on the day before {@code Snapshot.asOf}.
     */
    public record StockLine(Long facilityId, Long medicineId, List<BatchDto> batches,
                            List<Integer> dailyDemand, List<Integer> dailyDispensed) {
    }

    public record CaseSeries(String disease, List<Integer> dailyCases) {
    }

    /** One call gives the forecast service all the data it needs for a district. */
    public record Snapshot(LocalDate asOf, String district, int historyDays, List<Facility> facilities,
                           List<Medicine> medicines, List<StockLine> stock, List<CaseSeries> caseTrends) {
    }

    /** A stock ledger line with names resolved, for the live panel. */
    public record LiveTxn(LocalDateTime time, Long facilityId, String facilityName, Long medicineId,
                          String medicineName, String unit, String type, int quantity, int shortfall,
                          int balanceAfter, String reference) {
    }

    public record MinuteBucket(String minute, int units, int transactions) {
    }

    /** Today's movement for a district, read from the stock ledger. */
    public record Live(LocalDateTime serverTime, LocalDateTime ledgerSince, String district, boolean feedEnabled, double feedSpeed,
                       int issuedUnitsToday, int demandedUnitsToday, int issueCountToday, int shortEventsToday,
                       int receivedUnitsToday, int transferredUnitsToday, double fillRateTodayPct,
                       List<MinuteBucket> perMinute, List<LiveTxn> recent) {
    }

    public record TransferRequest(Long fromFacilityId, Long toFacilityId, Long medicineId, int quantity,
                                  Integer minShelfLifeDays, String reference) {
    }

    public record ConsumptionRequest(Long facilityId, Long medicineId, LocalDate date, int demanded, int dispensed) {
    }

    public record BatchRequest(Long facilityId, Long medicineId, String batchNo, int quantity,
                               LocalDate manufactureDate, LocalDate expiryDate) {
    }
}
