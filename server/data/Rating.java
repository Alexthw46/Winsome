package server.data;

import java.util.UUID;

public record Rating(UUID user, int rate, long timestamp) {

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Rating rating && this.user.equals(rating.user);
    }

    public record Pair(int pos, int neg) {
    }
}
