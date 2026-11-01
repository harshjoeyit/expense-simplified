package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.split.model.Friend;

import java.util.List;
import java.util.Optional;

public interface FriendRepository {
    Optional<Friend> findById(Integer id);
    Optional<Friend> findByName(String name);
    List<Friend> findAll();
    Friend save(Friend friend);
    void deleteById(Integer id);
    boolean existsByName(String name);
}
