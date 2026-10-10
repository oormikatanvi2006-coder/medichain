package com.medichain.inventory.service;

import com.medichain.inventory.dto.Dtos.BatchDto;
import com.medichain.inventory.dto.Dtos.CaseSeries;
import com.medichain.inventory.dto.Dtos.Snapshot;
import com.medichain.inventory.dto.Dtos.StockLine;
import com.medichain.inventory.model.Batch;
import com.medichain.inventory.model.CaseTrend;
import com.medichain.inventory.model.ConsumptionLog;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.Medicine;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Turns raw rows into the per-facility, per-medicine snapshot. Plain Java, no database access. */
public final class SnapshotAssembler {
    private SnapshotAssembler() {
    }

    public static Snapshot build(LocalDate asOf, String district, int days, List<Facility> facilities,
                                 List<Medicine> medicines, List<Batch> batches, List<ConsumptionLog> logs,
                                 List<CaseTrend> cases) {
        LocalDate from = asOf.minusDays(days);

        Map<String, List<BatchDto>> batchMap = new HashMap<>();
        for (Batch b : batches) {
            if (b.getQuantity() <= 0) {
                continue;
            }
            batchMap.computeIfAbsent(key(b.getFacilityId(), b.getMedicineId()), k -> new ArrayList<>())
                    .add(new BatchDto(b.getId(), b.getBatchNo(), b.getQuantity(), b.getManufactureDate(),
                            b.getExpiryDate()));
        }

        Map<String, int[]> demand = new HashMap<>();
        Map<String, int[]> dispensed = new HashMap<>();
        for (ConsumptionLog log : logs) {
            int idx = (int) ChronoUnit.DAYS.between(from, log.getLogDate());
            if (idx < 0 || idx >= days) {
                continue;
            }
            String k = key(log.getFacilityId(), log.getMedicineId());
            demand.computeIfAbsent(k, x -> new int[days])[idx] += log.getDemanded();
            dispensed.computeIfAbsent(k, x -> new int[days])[idx] += log.getDispensed();
        }

        List<StockLine> lines = new ArrayList<>();
        for (Facility f : facilities) {
            for (Medicine m : medicines) {
                String k = key(f.getId(), m.getId());
                List<BatchDto> bs = batchMap.getOrDefault(k, new ArrayList<>());
                bs.sort(Comparator.comparing(BatchDto::expiryDate));
                lines.add(new StockLine(f.getId(), m.getId(), bs,
                        toList(demand.getOrDefault(k, new int[days])),
                        toList(dispensed.getOrDefault(k, new int[days]))));
            }
        }

        Map<String, int[]> caseMap = new TreeMap<>();
        for (CaseTrend c : cases) {
            int idx = (int) ChronoUnit.DAYS.between(from, c.getReportDate());
            if (idx < 0 || idx >= days) {
                continue;
            }
            caseMap.computeIfAbsent(c.getDisease(), x -> new int[days])[idx] += c.getCases();
        }
        List<CaseSeries> series = new ArrayList<>();
        caseMap.forEach((disease, arr) -> series.add(new CaseSeries(disease, toList(arr))));

        return new Snapshot(asOf, district, days, facilities, medicines, lines, series);
    }

    private static String key(Long facilityId, Long medicineId) {
        return facilityId + ":" + medicineId;
    }

    private static List<Integer> toList(int[] arr) {
        return Arrays.stream(arr).boxed().toList();
    }
}
