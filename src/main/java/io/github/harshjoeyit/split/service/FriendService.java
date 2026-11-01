package io.github.harshjoeyit.split.service;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.repository.FriendRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class FriendService {

    private final FriendRepository friendRepository;

    public FriendService(FriendRepository friendRepository) {
        this.friendRepository = friendRepository;
    }

    public List<Friend> getAllFriends() {
        return friendRepository.findAll();
    }

    public Optional<Friend> findById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return friendRepository.findById(id);
    }

    public Optional<Friend> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return friendRepository.findByName(name.trim());
    }

    public Friend addFriend(String name, Long splitwiseId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Friend name cannot be blank");
        }
        String trimmedName = name.trim();
        if (friendRepository.existsByName(trimmedName)) {
            throw new DuplicateFriendException("Friend already exists with name: " + trimmedName);
        }
        Friend saved = friendRepository.save(Friend.of(trimmedName, splitwiseId));
        log.info("Added friend: [id={}, name={}]", saved.id(), saved.name());
        return saved;
    }

    public Friend updateFriend(Integer id, String name, String newName, Long splitwiseId) {
        if (id == null && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Must provide either friend ID or current name to update");
        }

        Optional<Friend> optFriend = id != null
                ? friendRepository.findById(id)
                : friendRepository.findByName(name.trim());

        if (optFriend.isEmpty()) {
            throw new IllegalArgumentException("Friend not found" + (id != null ? " with ID: " + id : " with name: " + name));
        }

        Friend existing = optFriend.get();
        String targetName = (newName != null && !newName.isBlank()) ? newName.trim() : existing.name();
        Long targetSwId = (splitwiseId != null) ? splitwiseId : existing.splitwiseId();

        if (!targetName.equalsIgnoreCase(existing.name()) && friendRepository.existsByName(targetName)) {
            throw new DuplicateFriendException("Another friend already exists with name: " + targetName);
        }

        Friend updated = friendRepository.save(new Friend(existing.id(), targetName, targetSwId));
        log.info("Updated friend: [id={}, name={}]", updated.id(), updated.name());
        return updated;
    }

    public void deleteFriend(Integer id, String name) {
        if (id == null && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Must provide either friend ID or name to delete");
        }

        Optional<Friend> optFriend = id != null
                ? friendRepository.findById(id)
                : friendRepository.findByName(name.trim());

        if (optFriend.isEmpty()) {
            throw new IllegalArgumentException("Friend not found" + (id != null ? " with ID: " + id : " with name: " + name));
        }

        friendRepository.deleteById(optFriend.get().id());
        log.info("Deleted friend: [id={}, name={}]", optFriend.get().id(), optFriend.get().name());
    }
}
