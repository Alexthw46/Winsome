package server;

import server.data.Comment;
import server.data.Post;
import server.data.Rating;

import java.util.*;

public class RewardsCalculator {

    public static List<CoinReward> getPointsFromPost(Post p, long lastCheckTime, float authorPercentage) {
        long total = 0;
        UUID author = ServerMain.userIdLookup.get(p.username());
        Rating.Pair likes = p.newTotalRating(lastCheckTime);
        Set<UUID> curators = new HashSet<>(p.postCurators());
        double pointsFromLikes = Math.log(1 + Math.max(0, likes.pos() - likes.neg()));
        double pointsFromComments = 0;
        Map<UUID, Integer> newComments = new HashMap<>();
        for (Comment c : p.comments()) {
            if (!c.userId().equals(author) && c.timestamp() > lastCheckTime) {
                newComments.put(c.userId(), newComments.getOrDefault(c.userId(), 0) + 1);
                curators.add(c.userId());
            }
        }
        for (Integer times : newComments.values()) {
            pointsFromComments += (2 / (1 + Math.exp(-(times - 1))));
        }
        pointsFromComments = Math.log(1 + pointsFromComments);

        p.timesChecked().setValue(p.timesChecked().getValue() + 1);

        total += (pointsFromComments + pointsFromLikes) / p.timesChecked().getValue();

        float authorReward = authorPercentage * total;
        float curatorReward = (1-authorPercentage) * total;

        List<CoinReward> result = new ArrayList<>();
        result.add(new CoinReward(author, authorReward));
        for (UUID c : curators){
            result.add(new CoinReward(c, curatorReward));
        }

        return result;
    }

    public record CoinReward(UUID user, float reward) {}

}
