package io.github.harshjoeyit.split.repository;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.config.FriendConfig;
import io.github.harshjoeyit.split.model.Friend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.shell.interactive.enabled=false",
        "spring.datasource.url=jdbc:sqlite:target/test-expense-simplified.db?journal_mode=WAL&foreign_keys=ON",
        "app.persistence=sqlite"
})
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class
})
class JdbcFriendRepositoryTest {

    @Autowired
    private JdbcFriendRepository friendRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM friends").update();
    }

    @Test
    void save_autoGeneratesIntegerId() {
        Friend friend = Friend.of("Ankit College", 12345L);
        Friend saved = friendRepository.save(friend);

        assertThat(saved.id()).isNotNull().isPositive();
        assertThat(saved.name()).isEqualTo("Ankit College");
        assertThat(saved.splitwiseId()).isEqualTo(12345L);

        Optional<Friend> found = friendRepository.findById(saved.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Ankit College");
    }

    @Test
    void findByName_isCaseInsensitive() {
        Friend saved = friendRepository.save(Friend.of("Sachin Tendulkar"));

        Optional<Friend> foundLower = friendRepository.findByName("sachin tendulkar");
        assertThat(foundLower).isPresent();
        assertThat(foundLower.get().id()).isEqualTo(saved.id());

        Optional<Friend> foundUpper = friendRepository.findByName("SACHIN TENDULKAR");
        assertThat(foundUpper).isPresent();
        assertThat(foundUpper.get().id()).isEqualTo(saved.id());
    }

    @Test
    void save_withDuplicateName_throwsDuplicateFriendException() {
        friendRepository.save(Friend.of("Alice"));

        // Case-insensitive duplicate name
        assertThatThrownBy(() -> friendRepository.save(Friend.of("alice")))
                .isInstanceOf(DuplicateFriendException.class)
                .hasMessageContaining("Friend already exists with name: alice");
    }

    @Test
    void save_withExistingId_updatesFriendDetails() {
        Friend original = friendRepository.save(Friend.of("Bob", 1111L));

        Friend toUpdate = new Friend(original.id(), "Bob Jones", 2222L);
        Friend updated = friendRepository.save(toUpdate);

        assertThat(updated.id()).isEqualTo(original.id());

        Optional<Friend> found = friendRepository.findById(original.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Bob Jones");
        assertThat(found.get().splitwiseId()).isEqualTo(2222L);
    }

    @Test
    void findAll_returnsAllFriendsSortedById() {
        friendRepository.save(Friend.of("Charlie"));
        friendRepository.save(Friend.of("Alice"));
        friendRepository.save(Friend.of("Bob"));

        List<Friend> all = friendRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).extracting(Friend::name).containsExactly("Charlie", "Alice", "Bob");
    }

    @Test
    void deleteById_removesFriend() {
        Friend saved = friendRepository.save(Friend.of("Dave"));
        assertThat(friendRepository.findById(saved.id())).isPresent();

        friendRepository.deleteById(saved.id());
        assertThat(friendRepository.findById(saved.id())).isEmpty();
    }

    @Test
    void existsByName_checksCaseInsensitively() {
        friendRepository.save(Friend.of("Eve"));

        assertThat(friendRepository.existsByName("eve")).isTrue();
        assertThat(friendRepository.existsByName("EVE")).isTrue();
        assertThat(friendRepository.existsByName("Adam")).isFalse();
    }

    @Test
    void init_seedsFromFriendConfig_withConflictIgnored() {
        FriendConfig.FriendConfigEntry seed1 = new FriendConfig.FriendConfigEntry("Seed Friend 1", 9999L);
        FriendConfig.FriendConfigEntry seed2 = new FriendConfig.FriendConfigEntry("Seed Friend 2", null);

        FriendConfig customConfig = new FriendConfig(List.of(seed1, seed2));
        JdbcFriendRepository customRepo = new JdbcFriendRepository(jdbcClient, customConfig);

        customRepo.init();

        Optional<Friend> found1 = customRepo.findByName("Seed Friend 1");
        assertThat(found1).isPresent();
        assertThat(found1.get().splitwiseId()).isEqualTo(9999L);

        // Re-run init() to simulate app restart
        customRepo.init();

        List<Friend> all = customRepo.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void init_restartsDoNotAdvanceSqliteSequence() {
        FriendConfig.FriendConfigEntry seed1 = new FriendConfig.FriendConfigEntry("Seed Friend 1", 9999L);
        FriendConfig.FriendConfigEntry seed2 = new FriendConfig.FriendConfigEntry("Seed Friend 2", null);

        FriendConfig customConfig = new FriendConfig(List.of(seed1, seed2));
        JdbcFriendRepository customRepo = new JdbcFriendRepository(jdbcClient, customConfig);

        customRepo.init();

        Integer seqAfterFirstInit = jdbcClient.sql("SELECT seq FROM sqlite_sequence WHERE name = 'friends'")
                .query(Integer.class)
                .single();

        // Re-run init multiple times simulating app restarts
        customRepo.init();
        customRepo.init();

        Integer seqAfterSubsequentInits = jdbcClient.sql("SELECT seq FROM sqlite_sequence WHERE name = 'friends'")
                .query(Integer.class)
                .single();

        assertThat(seqAfterSubsequentInits).isEqualTo(seqAfterFirstInit);

        // Verify next saved friend has contiguous id
        Friend nextFriend = customRepo.save(Friend.of("Next Friend"));
        assertThat(nextFriend.id()).isEqualTo(seqAfterFirstInit + 1);
    }
}
