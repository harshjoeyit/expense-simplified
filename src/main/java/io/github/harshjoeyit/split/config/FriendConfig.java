package io.github.harshjoeyit.split.config;

import io.github.harshjoeyit.split.model.Friend;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties
public record FriendConfig(
        List<Friend> friends
) {
}
