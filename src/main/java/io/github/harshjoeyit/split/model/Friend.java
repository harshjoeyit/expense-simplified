package io.github.harshjoeyit.split.model;

import java.util.Objects;

public record Friend(
        Integer id,
        String name,
        Long splitwiseId
) {
    public static final Friend SELF = new Friend(0, "Myself", null);

    public Friend {
        Objects.requireNonNull(name, "Friend name must not be null");
    }

    public static Friend of(String name) {
        return new Friend(null, name, null);
    }

    public static Friend of(String name, Long splitwiseId) {
        return new Friend(null, name, splitwiseId);
    }

    public boolean isSelf() {
        return Integer.valueOf(0).equals(this.id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Friend other)) return false;
        if (this.id != null && other.id != null) {
            return Objects.equals(this.id, other.id);
        }
        return this.name.equalsIgnoreCase(other.name);
    }

    @Override
    @SuppressWarnings("NonFinalFieldInGetHash") // Safe because record components are shallowly immutable
    public int hashCode() {
        return id != null ? Objects.hashCode(id) : name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name != null ? name : String.valueOf(id);
    }
}
