package com.medichain.lite;

import com.medichain.inventory.model.Batch;
import com.medichain.inventory.model.CaseTrend;
import com.medichain.inventory.model.ConsumptionLog;
import com.medichain.inventory.model.Facility;
import com.medichain.inventory.model.StockTransaction;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory tables that stand in for the database. Each repository interface from the two services is
 * backed by a list; the finder methods the services call are answered here by name.
 */
final class MemoryStore {
    /** One lock for all tables: the dispensing feed and web requests run on different threads. */
    static final Object LOCK = new Object();

    private MemoryStore() {
    }

    @SuppressWarnings("unchecked")
    static <R> R repository(Class<R> type) {
        List<Object> rows = new ArrayList<>();
        long[] nextId = {1};
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            String name = method.getName();
            synchronized (LOCK) {
            switch (name) {
                case "save":
                    return save(rows, nextId, args[0]);
                case "saveAll": {
                    List<Object> saved = new ArrayList<>();
                    for (Object o : (Iterable<Object>) args[0]) {
                        saved.add(save(rows, nextId, o));
                    }
                    return saved;
                }
                case "findAll":
                    return new ArrayList<>(rows);
                case "count":
                    return (long) rows.size();
                case "findById":
                    return rows.stream().filter(r -> Objects.equals(id(r), args[0])).findFirst();
                case "existsById":
                    return rows.stream().anyMatch(r -> Objects.equals(id(r), args[0]));
                case "toString":
                    return type.getSimpleName() + "[" + rows.size() + " rows]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return find(rows, name, args);
            }
            }
        });
    }

    private static Object save(List<Object> rows, long[] nextId, Object row) throws ReflectiveOperationException {
        if (id(row) == null) {
            row.getClass().getMethod("setId", Long.class).invoke(row, nextId[0]++);
            rows.add(row);
        }
        return row;
    }

    private static Object id(Object row) {
        return get(row, "getId");
    }

    private static Object get(Object row, String getter) {
        try {
            Method m = row.getClass().getMethod(getter);
            return m.invoke(row);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean same(Object a, Object b) {
        return a instanceof String && b instanceof String ? ((String) a).equalsIgnoreCase((String) b) : Objects.equals(a, b);
    }

    /** The derived queries declared on the repository interfaces. */
    private static Object find(List<Object> rows, String name, Object[] a) {
        switch (name) {
            // FacilityRepository
            case "findByDistrictIgnoreCaseOrderByCodeAsc":
                return filter(rows, r -> same(get(r, "getDistrict"), a[0]), Comparator.comparing(r -> ((Facility) r).getCode()));
            case "findDistricts":
                return rows.stream().map(r -> ((Facility) r).getDistrict()).distinct().sorted().collect(Collectors.toList());
            // BatchRepository
            case "findByFacilityIdIn":
                return filter(rows, r -> ((Collection<?>) a[0]).contains(get(r, "getFacilityId")), null);
            case "findByFacilityIdOrderByExpiryDateAsc":
                return filter(rows, r -> same(get(r, "getFacilityId"), a[0]), Comparator.comparing(r -> ((Batch) r).getExpiryDate()));
            case "findByFacilityIdAndMedicineIdOrderByExpiryDateAsc":
                return filter(rows, r -> same(get(r, "getFacilityId"), a[0]) && same(get(r, "getMedicineId"), a[1]),
                        Comparator.comparing(r -> ((Batch) r).getExpiryDate()));
            // ConsumptionLogRepository
            case "findByFacilityIdInAndLogDateGreaterThanEqual":
                return filter(rows, r -> ((Collection<?>) a[0]).contains(get(r, "getFacilityId"))
                        && !((ConsumptionLog) r).getLogDate().isBefore((LocalDate) a[1]), null);
            case "findByFacilityIdAndMedicineIdAndLogDateGreaterThanEqualOrderByLogDateAsc":
                return filter(rows, r -> same(get(r, "getFacilityId"), a[0]) && same(get(r, "getMedicineId"), a[1])
                        && !((ConsumptionLog) r).getLogDate().isBefore((LocalDate) a[2]),
                        Comparator.comparing(r -> ((ConsumptionLog) r).getLogDate()));
            case "findByFacilityIdAndMedicineIdAndLogDate":
                return filter(rows, r -> same(get(r, "getFacilityId"), a[0]) && same(get(r, "getMedicineId"), a[1])
                        && ((ConsumptionLog) r).getLogDate().equals(a[2]), null);
            // CaseTrendRepository
            case "findByDistrictIgnoreCaseAndReportDateGreaterThanEqual":
                return filter(rows, r -> same(get(r, "getDistrict"), a[0])
                        && !((CaseTrend) r).getReportDate().isBefore((LocalDate) a[1]), null);
            // StockTransactionRepository
            case "findByFacilityIdInAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc":
                return filter(rows, r -> ((Collection<?>) a[0]).contains(get(r, "getFacilityId"))
                        && !((StockTransaction) r).getOccurredAt().isBefore((LocalDateTime) a[1]),
                        Comparator.comparing((Object r) -> ((StockTransaction) r).getOccurredAt()).reversed());
            // AppUserRepository
            case "findByUsernameIgnoreCase":
                return rows.stream().filter(r -> same(get(r, "getUsername"), a[0])).findFirst();
            // RecommendationRepository
            case "findByDistrictIgnoreCaseAndStatus":
                return filter(rows, r -> same(get(r, "getDistrict"), a[0]) && Objects.equals(get(r, "getStatus"), a[1]), null);
            case "findAllByOrderByCreatedAtDesc": {
                List<Object> all = new ArrayList<>(rows);
                java.util.Collections.reverse(all);
                return all;
            }
            default:
                throw new UnsupportedOperationException("Query not supported by the in-memory store: " + name);
        }
    }

    private static List<Object> filter(List<Object> rows, Predicate<Object> test, Comparator<Object> order) {
        List<Object> out = rows.stream().filter(test).collect(Collectors.toCollection(ArrayList::new));
        if (order != null) {
            out.sort(order);
        }
        return out;
    }
}
