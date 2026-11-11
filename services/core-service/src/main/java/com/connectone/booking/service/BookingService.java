package com.connectone.booking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectone.booking.dto.BookingDtos.BookingResponse;
import com.connectone.booking.dto.BookingDtos.CreateRequest;
import com.connectone.admin.service.CheckoutCouponService;
import com.connectone.common.audit.AuditService;
import com.connectone.common.database.GeneratedKeySupport;
import com.connectone.common.database.JdbcRowSupport;
import com.connectone.common.exception.ApiException;
import com.connectone.notification.service.NotificationService;

@Service
public class BookingService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final CheckoutCouponService coupons;

    public BookingService(
            JdbcTemplate jdbcTemplate, AuditService auditService, NotificationService notifications,
            CheckoutCouponService coupons) {
        this.jdbcTemplate = jdbcTemplate;
        this.coupons = coupons;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalogue(String type, String query, String location) {
        return JdbcRowSupport.camelCaseRows(jdbcTemplate.queryForList(
                "SELECT id, item_type, item_code, title, description, category, location, starts_at, capacity, "
                        + "price, available FROM booking_catalog_item WHERE available = TRUE "
                        + "AND starts_at > CURRENT_TIMESTAMP AND (CAST(? AS VARCHAR) IS NULL OR item_type = ?) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR LOWER(title) LIKE LOWER(?)) "
                        + "AND (CAST(? AS VARCHAR) IS NULL OR LOWER(location) LIKE LOWER(?)) "
                        + "ORDER BY starts_at, id LIMIT 200",
                type, type, query, like(query), location, like(location)));
    }

    @Transactional(readOnly = true)
    public List<String> availability(long catalogItemId) {
        Map<String, Object> item = findItem(catalogItemId, false);
        if (!Boolean.TRUE.equals(item.get("available"))
                || !((Timestamp) item.get("starts_at")).toLocalDateTime().isAfter(LocalDateTime.now())) {
            return List.of();
        }
        int capacity = ((Number) item.get("capacity")).intValue();
        List<String> claimed = jdbcTemplate.query(
                "SELECT seat_or_slot FROM booking_seat_claim WHERE catalog_item_id = ? ORDER BY seat_or_slot",
                (result, row) -> result.getString(1), catalogItemId);
        java.util.Set<String> occupied = java.util.Set.copyOf(claimed);
        java.util.ArrayList<String> available = new java.util.ArrayList<>();
        for (int seat = 1; seat <= capacity; seat++) {
            String slot = Integer.toString(seat);
            if (!occupied.contains(slot)) {
                available.add(slot);
            }
        }
        return available;
    }

    @Transactional
    public BookingResponse create(long userId, CreateRequest request) {
        long customerId = activeCustomerId(userId);
        Map<String, Object> item = findItem(request.catalogItemId(), true);
        Map<String, Object> existing = findByIdempotency(customerId, request.idempotencyKey());
        if (existing != null) {
            if (((Number) existing.get("catalog_item_id")).longValue() != request.catalogItemId()
                    || !request.seatOrSlot().equals(existing.get("seat_or_slot"))) {
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key was already used for a different booking");
            }
            return get(((Number) existing.get("id")).longValue(), customerId);
        }
        LocalDateTime startsAt = ((Timestamp) item.get("starts_at")).toLocalDateTime();
        if (!Boolean.TRUE.equals(item.get("available")) || !startsAt.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "The schedule is not available for booking");
        }
        int capacity = ((Number) item.get("capacity")).intValue();
        int seat = Integer.parseInt(request.seatOrSlot());
        if (seat > capacity) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Seat or slot is outside the schedule capacity");
        }
        Long bookingId = insertBooking(customerId, item, request);
        try {
            jdbcTemplate.update(
                    "INSERT INTO booking_seat_claim (catalog_item_id, seat_or_slot, booking_id) VALUES (?, ?, ?)",
                    request.catalogItemId(), request.seatOrSlot(), bookingId);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "The requested seat or slot is no longer available");
        }
        GeneratedKeySupport.insert(jdbcTemplate,
                "INSERT INTO payment_record "
                        + "(customer_id, booking_id, amount, currency, status, idempotency_key) "
                        + "VALUES (?, ?, ?, 'INR', 'INITIATED', ?)",
                customerId, bookingId, item.get("price"), "booking-" + sha256(request.idempotencyKey()));
        coupons.applyToPayment(customerId, request.couponCode(), "booking-" + sha256(request.idempotencyKey()),
                (java.math.BigDecimal) item.get("price"));
        auditService.record(userId, "BOOKING_INITIATED", "BOOKING", bookingId);
        return get(bookingId, customerId);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> history(long userId) {
        long customerId = activeCustomerId(userId);
        return jdbcTemplate.query(
                bookingSelect() + "WHERE b.customer_id = ? ORDER BY b.created_at DESC, b.id DESC",
                (result, row) -> map(result),
                customerId);
    }

    @Transactional
    public BookingResponse cancel(long userId, long bookingId) {
        long customerId = activeCustomerId(userId);
        Map<String, Object> booking = queryBooking(bookingId, customerId, true);
        String status = (String) booking.get("status");
        if (!"PENDING".equals(status) && !"CONFIRMED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking cannot be cancelled in its current state");
        }
        long paymentId = ((Number) booking.get("payment_id")).longValue();
        if ("CONFIRMED".equals(status)) {
            Map<String, Object> payment = jdbcTemplate.queryForMap(
                    "SELECT amount, status FROM payment_record WHERE id = ? FOR UPDATE", paymentId);
            if (!"SUCCEEDED".equals(payment.get("status"))) {
                throw new ApiException(HttpStatus.CONFLICT, "Confirmed booking has no refundable payment");
            }
            jdbcTemplate.update(
                    "INSERT INTO refund_record (payment_id, amount, status, reason, idempotency_key) "
                            + "VALUES (?, ?, 'SUCCEEDED', 'Customer booking cancellation', ?)",
                    paymentId, payment.get("amount"), "booking-cancel-" + bookingId);
            jdbcTemplate.update(
                    "UPDATE payment_record SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    paymentId);
            jdbcTemplate.update(
                    "UPDATE booking_record SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP, "
                            + "version = version + 1 WHERE id = ? AND status = 'CONFIRMED'",
                    bookingId);
        } else {
            jdbcTemplate.update(
                    "UPDATE payment_record SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP "
                            + "WHERE id = ? AND status = 'INITIATED'",
                    paymentId);
            jdbcTemplate.update(
                    "UPDATE booking_record SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP, "
                            + "version = version + 1 WHERE id = ? AND status = 'PENDING'",
                    bookingId);
        }
        jdbcTemplate.update("DELETE FROM booking_seat_claim WHERE booking_id = ?", bookingId);
        auditService.record(userId, "BOOKING_CANCELLED", "BOOKING", bookingId);
        notifications.enqueue(customerId, "BOOKING_CANCELLED", "Booking cancelled",
                "Your booking " + booking.get("booking_code") + " was cancelled.",
                "booking-cancel-" + bookingId);
        return get(bookingId, customerId);
    }

    private Long insertBooking(long customerId, Map<String, Object> item, CreateRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String bookingCode = "CN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO booking_record "
                            + "(booking_code, customer_id, catalog_item_id, seat_or_slot, status, amount, idempotency_key) "
                            + "VALUES (?, ?, ?, ?, 'PENDING', ?, ?)",
                    new String[] {"id"});
            statement.setString(1, bookingCode);
            statement.setLong(2, customerId);
            statement.setLong(3, request.catalogItemId());
            statement.setString(4, request.seatOrSlot());
            statement.setBigDecimal(5, (java.math.BigDecimal) item.get("price"));
            statement.setString(6, request.idempotencyKey());
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("Database did not return the booking identifier");
        }
        return id.longValue();
    }

    private Map<String, Object> findItem(long id, boolean lock) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, item_type, title, starts_at, capacity, available, price FROM booking_catalog_item "
                            + "WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                    id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Schedule was not found");
        }
    }

    private Map<String, Object> findByIdempotency(long customerId, String key) {
        try {
            return jdbcTemplate.queryForMap(
                    bookingSelect() + "WHERE b.customer_id = ? AND b.idempotency_key = ?",
                    customerId, key);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Map<String, Object> queryBooking(long id, long customerId, boolean lock) {
        try {
            return jdbcTemplate.queryForMap(
                    bookingSelect() + "WHERE b.id = ? AND b.customer_id = ?" + (lock ? " FOR UPDATE OF b" : ""),
                    id, customerId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking was not found");
        }
    }

    private BookingResponse get(long id, long customerId) {
        return jdbcTemplate.queryForObject(
                bookingSelect() + "WHERE b.id = ? AND b.customer_id = ?",
                (result, row) -> map(result), id, customerId);
    }

    private String bookingSelect() {
        return "SELECT b.id, b.booking_code, b.catalog_item_id, i.item_type, i.title, b.seat_or_slot, "
                + "b.status, b.amount, p.id AS payment_id, i.starts_at, b.created_at "
                + "FROM booking_record b JOIN booking_catalog_item i ON i.id = b.catalog_item_id "
                + "LEFT JOIN payment_record p ON p.booking_id = b.id ";
    }

    private BookingResponse map(java.sql.ResultSet result) throws java.sql.SQLException {
        Timestamp startsAt = result.getTimestamp("starts_at");
        Timestamp createdAt = result.getTimestamp("created_at");
        long paymentId = result.getLong("payment_id");
        Long nullablePaymentId = result.wasNull() ? null : paymentId;
        return new BookingResponse(
                result.getLong("id"), result.getString("booking_code"), result.getLong("catalog_item_id"),
                result.getString("item_type"), result.getString("title"), result.getString("seat_or_slot"),
                result.getString("status"), result.getBigDecimal("amount"), nullablePaymentId,
                startsAt.toLocalDateTime(), createdAt.toLocalDateTime());
    }

    private long activeCustomerId(long userId) {
        Long id = jdbcTemplate.query(
                "SELECT id FROM customer_profile WHERE user_id = ? AND account_status = 'ACTIVE'",
                result -> result.next() ? result.getLong(1) : null, userId);
        if (id == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Customer profile was not found");
        }
        return id;
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.strip() + "%";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash an idempotency key", exception);
        }
    }
}
