package io.github.harshjoeyit.cli.command;

import io.github.harshjoeyit.core.exception.DuplicateFriendException;
import io.github.harshjoeyit.split.model.Friend;
import io.github.harshjoeyit.split.service.FriendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.table.ArrayTableModel;
import org.springframework.shell.table.BorderStyle;
import org.springframework.shell.table.TableBuilder;
import org.springframework.shell.table.TableModel;

import java.util.List;
import java.util.Optional;

@Slf4j
@Command(group = "Friend Commands")
public class FriendCommands {

    private final FriendService friendService;

    public FriendCommands(FriendService friendService) {
        this.friendService = friendService;
    }

    @Command(
            command = "friends",
            alias = "friend",
            description = "List all configured friends"
    )
    public String listFriends() {
        List<Friend> friends = friendService.getAllFriends();
        if (friends.isEmpty()) {
            return "No friends found. Use 'friend-add --name <name>' to add one.";
        }
        return renderFriendsTable(friends);
    }

    @Command(
            command = "friend-add",
            description = "Add a new friend"
    )
    public String addFriend(
            @Option(longNames = "name", shortNames = 'n', required = true, description = "Unique name of the friend") String name,
            @Option(longNames = "splitwise-id", shortNames = 's', description = "Splitwise user ID (optional)") Long splitwiseId
    ) {
        try {
            Friend saved = friendService.addFriend(name, splitwiseId);
            return String.format("✓ Friend added: [%d] %s%s",
                    saved.id(), saved.name(), saved.splitwiseId() != null ? " (Splitwise ID: " + saved.splitwiseId() + ")" : "");
        } catch (IllegalArgumentException | DuplicateFriendException e) {
            return "⚠ " + e.getMessage();
        }
    }

    @Command(
            command = "friend-update",
            description = "Update an existing friend's name or Splitwise ID"
    )
    public String updateFriend(
            @Option(longNames = "id", shortNames = 'i', description = "Friend ID") Integer id,
            @Option(longNames = "name", shortNames = 'n', description = "Current friend name") String name,
            @Option(longNames = "new-name", description = "New name for the friend") String newName,
            @Option(longNames = "splitwise-id", shortNames = 's', description = "New Splitwise ID") Long splitwiseId
    ) {
        try {
            Friend updated = friendService.updateFriend(id, name, newName, splitwiseId);
            return String.format("✓ Friend updated: [%d] %s%s",
                    updated.id(), updated.name(), updated.splitwiseId() != null ? " (Splitwise ID: " + updated.splitwiseId() + ")" : "");
        } catch (IllegalArgumentException | DuplicateFriendException e) {
            return "⚠ " + e.getMessage();
        }
    }

    @Command(
            command = "friend-delete",
            description = "Delete a friend"
    )
    public String deleteFriend(
            @Option(longNames = "id", shortNames = 'i', description = "Friend ID") Integer id,
            @Option(longNames = "name", shortNames = 'n', description = "Friend name") String name
    ) {
        try {
            Optional<Friend> optFriend = id != null
                    ? friendService.findById(id)
                    : friendService.findByName(name);

            if (optFriend.isEmpty()) {
                return "⚠ Friend not found" + (id != null ? " with ID: " + id : " with name: " + name);
            }

            friendService.deleteFriend(id, name);
            return String.format("✓ Deleted friend: [%d] %s", optFriend.get().id(), optFriend.get().name());
        } catch (IllegalArgumentException e) {
            return "⚠ " + e.getMessage();
        }
    }

    private String renderFriendsTable(List<Friend> friends) {
        String[][] data = new String[friends.size() + 1][3];
        data[0] = new String[]{"ID", "Name", "Splitwise ID"};

        for (int i = 0; i < friends.size(); i++) {
            Friend f = friends.get(i);
            data[i + 1] = new String[]{
                    String.valueOf(f.id()),
                    f.name(),
                    f.splitwiseId() != null ? String.valueOf(f.splitwiseId()) : "-"
            };
        }

        TableModel model = new ArrayTableModel(data);
        TableBuilder tableBuilder = new TableBuilder(model);
        tableBuilder.addFullBorder(BorderStyle.fancy_light);
        return tableBuilder.build().render(80);
    }
}
