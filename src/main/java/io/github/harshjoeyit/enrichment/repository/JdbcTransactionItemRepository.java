package io.github.harshjoeyit.enrichment.repository;

import io.github.harshjoeyit.enrichment.model.TransactionItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcTransactionItemRepository implements TransactionItemRepository {

    private final JdbcClient jdbcClient;

    private static final RowMapper<TransactionItem> ROW_MAPPER = (rs, rowNum) -> {
        double unitPriceVal = rs.getDouble("unit_price");
        Double unitPrice = rs.wasNull() ? null : unitPriceVal;

        String createdAtStr = rs.getString("created_at");
        LocalDateTime createdAt = createdAtStr != null
                ? LocalDateTime.parse(createdAtStr.replace(" ", "T"))
                : LocalDateTime.now();

        return TransactionItem.builder()
                .id(rs.getLong("id"))
                .transactionId(UUID.fromString(rs.getString("transaction_id")))
                .itemName(rs.getString("item_name"))
                .quantity(rs.getInt("quantity"))
                .unitPrice(unitPrice)
                .totalPrice(rs.getDouble("total_price"))
                .outletName(rs.getString("outlet_name"))
                .platform(rs.getString("platform"))
                .platformOrderId(rs.getString("platform_order_id"))
                .createdAt(createdAt)
                .build();
    };

    public JdbcTransactionItemRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public TransactionItem save(TransactionItem item) {
        if (item == null) {
            throw new IllegalArgumentException("TransactionItem must not be null");
        }

        if (item.id() != null) {
            String updateSql = """
                    UPDATE transaction_items SET
                        transaction_id = :transactionId,
                        item_name = :itemName,
                        quantity = :quantity,
                        unit_price = :unitPrice,
                        total_price = :totalPrice,
                        outlet_name = :outletName,
                        platform = :platform,
                        platform_order_id = :platformOrderId
                    WHERE id = :id
                    """;
            jdbcClient.sql(updateSql)
                    .param("id", item.id())
                    .param("transactionId", item.transactionId().toString())
                    .param("itemName", item.itemName())
                    .param("quantity", item.quantity())
                    .param("unitPrice", item.unitPrice())
                    .param("totalPrice", item.totalPrice())
                    .param("outletName", item.outletName())
                    .param("platform", item.platform())
                    .param("platformOrderId", item.platformOrderId())
                    .update();
            return item;
        }

        String insertSql = """
                INSERT INTO transaction_items (
                    transaction_id, item_name, quantity, unit_price, total_price,
                    outlet_name, platform, platform_order_id, created_at
                ) VALUES (
                    :transactionId, :itemName, :quantity, :unitPrice, :totalPrice,
                    :outletName, :platform, :platformOrderId, :createdAt
                ) RETURNING id
                """;

        Long generatedId = jdbcClient.sql(insertSql)
                .param("transactionId", item.transactionId().toString())
                .param("itemName", item.itemName())
                .param("quantity", item.quantity())
                .param("unitPrice", item.unitPrice())
                .param("totalPrice", item.totalPrice())
                .param("outletName", item.outletName())
                .param("platform", item.platform())
                .param("platformOrderId", item.platformOrderId())
                .param("createdAt", item.createdAt().toString())
                .query(Long.class)
                .single();

        return item.withId(generatedId);
    }

    @Override
    public List<TransactionItem> saveAll(List<TransactionItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TransactionItem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM transaction_items WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<TransactionItem> findByTransactionId(UUID transactionId) {
        if (transactionId == null) {
            return Collections.emptyList();
        }
        return jdbcClient.sql("SELECT * FROM transaction_items WHERE transaction_id = :transactionId ORDER BY id ASC")
                .param("transactionId", transactionId.toString())
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public Map<UUID, List<TransactionItem>> findByTransactionIds(Collection<UUID> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> idStrings = transactionIds.stream().map(UUID::toString).toList();
        List<TransactionItem> list = jdbcClient.sql(
                        """
                        SELECT * FROM transaction_items
                        WHERE transaction_id IN (:ids)
                        ORDER BY id ASC
                       """)
                .param("ids", idStrings)
                .query(ROW_MAPPER)
                .list();

        return list.stream()
                .collect(Collectors.groupingBy(TransactionItem::transactionId));
    }

    @Override
    public void deleteByTransactionId(UUID transactionId) {
        if (transactionId == null) {
            log.warn("Invalid transaction Id null");
            return;
        }
        jdbcClient.sql("DELETE FROM transaction_items WHERE transaction_id = :transactionId")
                .param("transactionId", transactionId.toString())
                .update();
    }
}
