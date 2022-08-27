package server.data;

import java.util.UUID;

/**
 * @param user the id of the user
 * @param rate +1 if positive, -1 if negative
 * @param timestamp when the rating was given, in milliseconds
 */
public record Rating(UUID user, int rate, long timestamp) {

    /**
     * @param obj the reference object with which to compare.
     * @return true if the two rating objs have the same user id, used by Post#ratings set to handle duplicates
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Rating rating && rating.hashCode() == this.hashCode();
    }

    @Override
    public int hashCode() {
        return user.hashCode();
    }

    public record Pair(int pos, int neg) {
    }

}
