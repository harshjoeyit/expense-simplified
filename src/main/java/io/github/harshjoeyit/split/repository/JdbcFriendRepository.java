package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.config.FriendConfig;
import io.github.harshjoeyit.split.model.Friend;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "sqlite", matchIfMissing = true)
public class JdbcFriendRepository implements FriendRepository {

    private final JdbcClient jdbcClient;
    private final FriendConfig friendConfig;

    private static final RowMapper<Friend> ROW_MAPPER = (rs, rowNum) -> {
        long swIdVal = rs.getLong("splitwise_id");
        Long splitwiseId = rs.wasNull() ? null : swIdVal;
        return new Friend(
                rs.getInt("id"),
                rs.getString("name"),
                splitwiseId
        );
    };

    public JdbcFriendRepository(JdbcClient jdbcClient, FriendConfig friendConfig) {
        this.jdbcClient = jdbcClient;
        this.friendConfig = friendConfig;
    }

    @PostConstruct
    public void init() {
        if (friendConfig == null || friendConfig.toFriends().isEmpty()) {
            return;
        }

        log.info("Checking friends seed config: {} friends found", friendConfig.toFriends().size());
        String sql = """
                INSERT INTO friends (name, splitwise_id, created_at, updated_at)
                VALUES (:name, :splitwiseId, :createdAt, :updatedAt)
                ON CONFLICT(name) DO NOTHING
                """;

        String now = LocalDateTime.now().toString();
        for (Friend friend : friendConfig.toFriends()) {
            if (friend == null || friend.name() == null || friend.name().isBlank()) {
                log.warn("Invalid friend: {}. Skipping to load in database", friend);
                continue;
            }
            if (existsByName(friend.name())) {
                log.debug("Friend {} already exists in database, skipping seed insert", friend.name());
                continue;
            }
            jdbcClient.sql(sql)
                    .param("name", friend.name().trim())
                    .param("splitwiseId", friend.splitwiseId())
                    .param("createdAt", now)
                    .param("updatedAt", now)
                    .update();
        }
    }

    @Override
    public Optional<Friend> findById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM friends WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Friend> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("SELECT * FROM friends WHERE name = :name COLLATE NOCASE")
                .param("name", name.trim())
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<Friend> findAll() {
        return jdbcClient.sql("SELECT * FROM friends ORDER BY id ASC")
                .query(ROW_MAPPER)
                .list();
    }

    // Todo: add a method which returns all friends ordered by historical splits (the one involved in most splits at the
    //  top, while the one with least/no splits at bottom. This will be useful in the user dropdown in split command
    //  make this configurable. Downside is order of friends in list may keep changing and user get accustomed to a order
    //  and they may not like it.

    @Override
    public Friend save(Friend friend) {
        if (friend == null || friend.name() == null || friend.name().isBlank()) {
            throw new IllegalArgumentException("Friend name must not be blank");
        }

        String now = LocalDateTime.now().toString();

        if (friend.id() == null) {
            if (existsByName(friend.name())) {
                throw new DuplicateFriendException("Friend already exists with name: " + friend.name().trim());
            }

            String sql = """
                    INSERT INTO friends (name, splitwise_id, created_at, updated_at)
                    VALUES (:name, :splitwiseId, :createdAt, :updatedAt)
                    RETURNING id
                    """;

            Integer generatedId = jdbcClient.sql(sql)
                    .param("name", friend.name().trim())
                    .param("splitwiseId", friend.splitwiseId())
                    .param("createdAt", now)
                    .param("updatedAt", now)
                    .query(Integer.class)
                    .single();

            log.debug("Saved new friend: {} with Id: ", friend, generatedId);
            return new Friend(generatedId, friend.name().trim(), friend.splitwiseId());
        } else {
            Optional<Friend> existingByName = findByName(friend.name());
            if (existingByName.isPresent() && !existingByName.get().id().equals(friend.id())) {
                throw new DuplicateFriendException("Another friend already exists with name: " + friend.name().trim());
            }

            String sql = """
                    UPDATE friends
                    SET name = :name,
                        splitwise_id = :splitwiseId,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """;

            jdbcClient.sql(sql)
                    .param("id", friend.id())
                    .param("name", friend.name().trim())
                    .param("splitwiseId", friend.splitwiseId())
                    .param("updatedAt", now)
                    .update();

            log.debug("Updated friend: {}", friend);
            return friend;
        }
    }

    @Override
    public void deleteById(Integer id) {
        if (id == null) {
            return;
        }
        jdbcClient.sql("DELETE FROM friends WHERE id = :id")
                .param("id", id)
                .update();
        log.debug("Deleted friend id: {}", id);
    }

    @Override
    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM friends WHERE name = :name COLLATE NOCASE")
                .param("name", name.trim())
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
