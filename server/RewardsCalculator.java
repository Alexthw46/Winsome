package server;

import server.data.Comment;
import server.data.Post;
import server.data.Rating;

import java.util.*;

public class RewardsCalculator {

    /**
     * @param postToCheck post
     * @param lastCheckTime timestamp of the last check
     * @param authorPercentage percentage of points for the post author
     * @return set of CoinRewards, pairing each username with the amount of coins to award
     */
    public static Set<CoinReward> getPointsFromPost(Post postToCheck, long lastCheckTime, float authorPercentage) {
        long total = 0;
        String author = postToCheck.author();

        //get new likes and who assigned them
        Set<String> curators = new HashSet<>(postToCheck.postCurators());
        Rating.Pair likes = postToCheck.newTotalRating(lastCheckTime);
        double pointsFromLikes = Math.log(1 + Math.max(0, likes.pos() - likes.neg()));
        //get new comments and who wrote them
        double pointsFromComments = 0;
        Map<String, Integer> newComments = new HashMap<>();
        for (Comment c : postToCheck.comments()) {
            if (!c.author().equals(author) && c.timestamp() > lastCheckTime) {
                newComments.put(c.author(), newComments.getOrDefault(c.author(), 0) + 1);
                curators.add(c.author());
            }
        }
        for (Integer times : newComments.values()) {
            pointsFromComments += (2 / (1 + Math.exp(-(times - 1))));
        }
        pointsFromComments = Math.log(1 + pointsFromComments);

        //update post checks counter
        postToCheck.timesChecked().setValue(postToCheck.timesChecked().getValue() + 1);

        //compute the total points and split them between author and curators
        total += (pointsFromComments + pointsFromLikes) / postToCheck.timesChecked().getValue();

        float authorReward = authorPercentage * total;
        float curatorReward = (1-authorPercentage) * total / Math.max(curators.size(), 1);

        Set<CoinReward> result = new HashSet<>();
        result.add(new CoinReward(author, authorReward));
        for (String c : curators){
            result.add(new CoinReward(c, curatorReward));
        }

        return result;
    }

    public record CoinReward(String user, float reward) {
        @Override
        public int hashCode() {
            return user.hashCode();
        }
    }


}
