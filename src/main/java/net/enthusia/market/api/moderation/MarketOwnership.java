package net.enthusia.market.api.moderation;

import java.util.Objects;
import java.util.Optional;

public record MarketOwnership(Type type, Optional<String> ownerId) {
    public MarketOwnership {
        type = Objects.requireNonNull(type, "type");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        if (type == Type.NONE && ownerId.isPresent()) {
            throw new IllegalArgumentException("unowned market ownership cannot contain an id");
        }
        if (type != Type.NONE) {
            final String value = ownerId.orElseThrow(() -> new IllegalArgumentException(
                    "owned market ownership requires an id"
            ));
            MarketApiValidation.identifier(value, "ownership id", 128);
        }
    }

    /** Compatibility accessor preserving the original record-style API name. */
    public Optional<String> id() {
        return ownerId;
    }

    /** Bean-style aliases use nullable values expected by older Staff reflection. */
    public Type getType() {
        return type;
    }

    public String getId() {
        return ownerId.orElse(null);
    }

    public enum Type {
        NONE,
        SOLO,
        GUILD
    }
}
