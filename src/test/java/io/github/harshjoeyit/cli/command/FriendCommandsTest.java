package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendCommandsTest {

    @Mock
    private FriendService friendService;

    private FriendCommands friendCommands;

    @BeforeEach
    void setUp() {
        friendCommands = new FriendCommands(friendService);
    }

    @Test
    void listFriends_whenEmpty_returnsHelpMessage() {
        when(friendService.getAllFriends()).thenReturn(List.of());

        String result = friendCommands.listFriends();
        assertThat(result).contains("No friends found");
    }

    @Test
    void listFriends_withFriends_rendersTable() {
        when(friendService.getAllFriends()).thenReturn(List.of(
                new Friend(1, "Alice", 1234L),
                new Friend(2, "Bob", null)
        ));

        String result = friendCommands.listFriends();
        assertThat(result).contains("Alice", "Bob", "1234");
    }

    @Test
    void addFriend_withValidName_savesAndReturnsSuccess() {
        when(friendService.addFriend("Charlie", 5678L)).thenReturn(new Friend(3, "Charlie", 5678L));

        String result = friendCommands.addFriend("Charlie", 5678L);
        assertThat(result).contains("Friend added", "[3] Charlie", "5678");
    }

    @Test
    void addFriend_whenDuplicate_returnsError() {
        when(friendService.addFriend("Alice", null))
                .thenThrow(new DuplicateFriendException("Friend already exists with name: Alice"));

        String result = friendCommands.addFriend("Alice", null);
        assertThat(result).contains("already exists");
    }

    @Test
    void addFriend_withBlankName_returnsError() {
        when(friendService.addFriend("   ", null))
                .thenThrow(new IllegalArgumentException("Friend name cannot be blank"));

        String result = friendCommands.addFriend("   ", null);
        assertThat(result).contains("cannot be blank");
    }

    @Test
    void updateFriend_updatesDetails() {
        when(friendService.updateFriend(null, "Alice", "Alice Smith", null))
                .thenReturn(new Friend(1, "Alice Smith", 1234L));

        String result = friendCommands.updateFriend(null, "Alice", "Alice Smith", null);
        assertThat(result).contains("Friend updated", "Alice Smith");
    }

    @Test
    void updateFriend_whenNotFound_returnsError() {
        when(friendService.updateFriend(99, null, "New Name", null))
                .thenThrow(new IllegalArgumentException("Friend not found with ID: 99"));

        String result = friendCommands.updateFriend(99, null, "New Name", null);
        assertThat(result).contains("Friend not found with ID: 99");
    }

    @Test
    void deleteFriend_removesFriend() {
        Friend existing = new Friend(2, "Bob", null);
        when(friendService.findByName("Bob")).thenReturn(Optional.of(existing));

        String result = friendCommands.deleteFriend(null, "Bob");
        assertThat(result).contains("Deleted friend: [2] Bob");
        verify(friendService).deleteFriend(null, "Bob");
    }

    @Test
    void deleteFriend_whenNotFound_returnsError() {
        when(friendService.findById(99)).thenReturn(Optional.empty());

        String result = friendCommands.deleteFriend(99, null);
        assertThat(result).contains("Friend not found with ID: 99");
    }
}
