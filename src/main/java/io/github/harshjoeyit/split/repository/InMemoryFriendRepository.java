package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.config.FriendConfig;
import io.github.harshjoeyit.split.model.Friend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Repository
@ConditionalOnProperty(name = "app.persistence", havingValue = "in-memory")
public class InMemoryFriendRepository implements FriendRepository {

    private final Map<Integer, Friend> friendsById = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public InMemoryFriendRepository(FriendConfig friendConfig) {
        if (friendConfig != null) {
            for (Friend friend : friendConfig.toFriends()) {
                if (friend != null && friend.name() != null) {
                    int id = friend.id() != null ? friend.id() : idGenerator.getAndIncrement();
                    friendsById.put(id, new Friend(id, friend.name(), friend.splitwiseId()));
                }
            }
        }
    }

    @Override
    public Optional<Friend> findById(Integer id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(friendsById.get(id));
    }

    @Override
    public Optional<Friend> findByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return friendsById.values().stream()
                .filter(f -> f.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    @Override
    public List<Friend> findAll() {
        return friendsById.values().stream()
                .sorted(Comparator.comparing(Friend::id))
                .toList();
    }

    @Override
    public Friend save(Friend friend) {
        if (friend == null || friend.name() == null || friend.name().isBlank()) {
            throw new IllegalArgumentException("Friend name must not be blank");
        }
        if (friend.id() == null) {
            if (existsByName(friend.name())) {
                throw new DuplicateFriendException("Friend already exists with name: " + friend.name().trim());
            }
            // save new friend
            int newId = idGenerator.getAndIncrement();
            Friend saved = new Friend(newId, friend.name().trim(), friend.splitwiseId());
            friendsById.put(newId, saved);
            return saved;
        } else {
            Optional<Friend> existingByName = findByName(friend.name());
            if (existingByName.isPresent() && !existingByName.get().id().equals(friend.id())) {
                throw new DuplicateFriendException("Another friend already exists with name: " + friend.name().trim());
            }
            friendsById.put(friend.id(), friend);
            return friend;
        }
    }

    @Override
    public void deleteById(Integer id) {
        if (id != null) {
            friendsById.remove(id);
        }
    }

    @Override
    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) return false;
        return friendsById.values().stream()
                .anyMatch(f -> f.name().equalsIgnoreCase(name.trim()));
    }
}
