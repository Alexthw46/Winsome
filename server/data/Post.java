package server.data;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @param username author username
 * @param postId id of the post
 * @param title title of the post
 * @param content content of the post
 * @param comments list of comments left on the post, thread-safe list
 * @param ratings list of ratings left on the post, thread-safe set (a user can only rate once)
 * @param timesChecked times the post have been checked for wincoins rewards
 */
public record Post(String username, int postId, String title, String content, List<Comment> comments, Set<Rating> ratings, NumberWrapper<Integer> timesChecked) implements Comparable<Post>{

    public Post(String userId, int postId, String title, String content){
        this(userId, postId, title, content, new CopyOnWriteArrayList<>(), new CopyOnWriteArraySet<>(), new NumberWrapper<>(0));
    }

    public synchronized void rate(UUID user, int voto) {
        this.ratings.add(new Rating(user, voto, System.currentTimeMillis()));
    }

    public synchronized void comment(Comment comment){
        this.comments().add(comment);
    }

    /**
     * @return pair with <sum of positive ratings, sum of negative ratings>
     */
    public synchronized Rating.Pair totalRating(){
        int pos = 0;
        int neg = 0;
        for (Rating like : ratings){
            if (like.rate() < 0){
                neg++;
            }else{
                pos++;
            }
        }
        return new Rating.Pair(pos, neg);
    }

    /**
     * Variation of Post#totalRating that only counts new ratings
     * @param lastTimeChecked timestamp of the last check in milliseconds
     * @return pair with <sum of positive ratings, sum of negative ratings> added after the last check
     */
    public synchronized Rating.Pair newTotalRating(long lastTimeChecked){
        int pos = 0;
        int neg = 0;
        for (Rating like : ratings){
            if (like.timestamp() < lastTimeChecked) continue; //only sum the new likes
            if (like.rate() < 0){
                neg++;
            }else{
                pos++;
            }
        }
        return new Rating.Pair(pos, neg);
    }

    /**
     * @param obj the reference object with which to compare.
     * @return true only if the postId is the same
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Post post && post.postId() == this.postId();
    }

    /**
     * @return formatted post as String
     */
    public String format(){
        Rating.Pair rating = totalRating();
        String formattedComments = "\n";
        for (Comment comment : comments()) {
            formattedComments = comment.format().concat(formattedComments);
        }
        if (comments.size() == 0) formattedComments = "No comments yet.\n";

        return  "Titolo: " + title() + '\n' +
                "Contenuto: " + content() + '\n' +
                "Voti: " + rating.pos() + " positivi, " + rating.neg() + " negativi" + '\n' +
                "Commenti:\n" + formattedComments;
    }

    /**
     * @param toCompare the object to be compared.
     * @return fallback to Integer comparator using the postId
     */
    @Override
    public int compareTo(Post toCompare) {
        return Integer.compare(this.postId(), toCompare.postId());
    }

    /**
     * @return List of the curators of the post, aka the users who left a positive rating
     */
    public Collection<UUID> postCurators() {
        List<UUID> curators = new ArrayList<>();
        for (Rating rating: ratings){
            //curators are the users that left a like
           if (rating.rate() > 0) curators.add(rating.user());
        }
        return curators;
    }
}
