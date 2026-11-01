package io.github.harshjoeyit.split.service;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.repository.FriendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;

    private FriendService friendService;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(friendRepository);
    }

    @Test
    void getAllFriends_delegatesToRepository() {
        when(friendRepository.findAll()).thenReturn(List.of(
                new Friend(1, "Alice", 1234L),
                new Friend(2, "Bob", null)
        ));

        List<Friend> result = friendService.getAllFriends();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Alice");
    }

    @Test
    void findById_and_findByName_delegatesToRepository() {
        Friend alice = new Friend(1, "Alice", 1234L);
        when(friendRepository.findById(1)).thenReturn(Optional.of(alice));
        when(friendRepository.findByName("Alice")).thenReturn(Optional.of(alice));

        assertThat(friendService.findById(1)).contains(alice);
        assertThat(friendService.findByName("Alice")).contains(alice);
        assertThat(friendService.findById(null)).isEmpty();
        assertThat(friendService.findByName("   ")).isEmpty();
    }

    @Test
    void addFriend_withValidName_savesAndReturns() {
        when(friendRepository.existsByName("Charlie")).thenReturn(false);
        when(friendRepository.save(any(Friend.class))).thenReturn(new Friend(3, "Charlie", 5678L));

        Friend saved = friendService.addFriend("Charlie", 5678L);
        assertThat(saved.id()).isEqualTo(3);
        assertThat(saved.name()).isEqualTo("Charlie");
    }

    @Test
    void addFriend_withBlankName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> friendService.addFriend("   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Friend name cannot be blank");
    }

    @Test
    void addFriend_whenDuplicate_throwsDuplicateFriendException() {
        when(friendRepository.existsByName("Alice")).thenReturn(true);

        assertThatThrownBy(() -> friendService.addFriend("Alice", null))
                .isInstanceOf(DuplicateFriendException.class)
                .hasMessageContaining("Friend already exists with name: Alice");
    }

    @Test
    void updateFriend_byName_updatesDetails() {
        Friend existing = new Friend(1, "Alice", 1234L);
        when(friendRepository.findByName("Alice")).thenReturn(Optional.of(existing));
        when(friendRepository.save(any(Friend.class))).thenReturn(new Friend(1, "Alice Smith", 9999L));

        Friend updated = friendService.updateFriend(null, "Alice", "Alice Smith", 9999L);
        assertThat(updated.name()).isEqualTo("Alice Smith");
        verify(friendRepository).save(new Friend(1, "Alice Smith", 9999L));
    }

    @Test
    void updateFriend_whenNewNameCollides_throwsDuplicateFriendException() {
        Friend existing = new Friend(1, "Alice", 1234L);
        when(friendRepository.findById(1)).thenReturn(Optional.of(existing));
        when(friendRepository.existsByName("Bob")).thenReturn(true);

        assertThatThrownBy(() -> friendService.updateFriend(1, null, "Bob", null))
                .isInstanceOf(DuplicateFriendException.class)
                .hasMessageContaining("Another friend already exists with name: Bob");
    }

    @Test
    void updateFriend_whenNotFound_throwsIllegalArgumentException() {
        when(friendRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.updateFriend(99, null, "Name", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Friend not found with ID: 99");
    }

    @Test
    void deleteFriend_byId_deletesFriend() {
        Friend existing = new Friend(2, "Bob", null);
        when(friendRepository.findById(2)).thenReturn(Optional.of(existing));

        friendService.deleteFriend(2, null);
        verify(friendRepository).deleteById(2);
    }

    @Test
    void deleteFriend_whenNotFound_throwsIllegalArgumentException() {
        when(friendRepository.findByName("Unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.deleteFriend(null, "Unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Friend not found with name: Unknown");
    }
}
