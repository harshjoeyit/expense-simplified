package io.github.harshjoeyit.split.model;

import java.util.Objects;

public record Friend(
        String id,
        String name,
        String splitwiseId
) {
    public static final Friend SELF = new Friend("self", "Myself", null);

    public Friend {
        Objects.requireNonNull(id, "Friend id must not be null");
    }

    public boolean isSelf() {
        return "self".equalsIgnoreCase(this.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Friend other)) return false;
        return Objects.equals(this.id, other.id);
    }

    @Override
    @SuppressWarnings("NonFinalFieldInGetHash") // Safe because record components are shallowly immutable
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return name != null ? name : id;
    }
}