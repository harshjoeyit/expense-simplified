package io.github.harshjoeyit.split.config;

import io.github.harshjoeyit.split.model.Friend;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties
public record FriendConfig(
        List<FriendConfigEntry> friends
) {
    public record FriendConfigEntry(
            String name,
            Long splitwiseId
    ) {
        public Friend toFriend() {
            return Friend.of(name, splitwiseId);
        }
    }

    public List<Friend> toFriends() {
        if (friends == null) {
            return List.of();
        }
        return friends.stream().map(FriendConfigEntry::toFriend).toList();
    }
}
