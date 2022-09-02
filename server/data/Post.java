package server.data;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @param author author of the post
 * @param postId id of the post
 * @param title title of the post
 * @param content content of the post
 * @param comments list of comments left on the post, thread-safe list
 * @param ratings list of ratings left on the post, thread-safe set (a user can only rate once)
 * @param timesChecked times the post have been checked for wincoins rewards
 */
public record Post(String author, int postId, String title, String content, CopyOnWriteArrayList<Comment> comments, CopyOnWriteArraySet<Rating> ratings, IntWrapper timesChecked) implements Comparable<Post>{

    public Post(String userId, int postId, String title, String content){
        this(userId, postId, title, content, new CopyOnWriteArrayList<>(), new CopyOnWriteArraySet<>(), new IntWrapper(0));
    }

    /**
     * Synchronized method to add a rating
     * @param vote rating to add
     */
    public synchronized boolean rate(String user, int vote) {
        return this.ratings.add(new Rating(user, vote, System.currentTimeMillis()));
    }

    /**
     * Synchronized method to add a comment
     * @param comment comment to add
     */
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
            if (like.rate() > 0) {
                pos++;
            } else {
                neg++;
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
        String formattedComments = "";

        if (comments.size() == 0){
            formattedComments = "No comments yet.\n";
        }else for (Comment comment : comments()) {
            formattedComments = formattedComments.concat(comment.format());
        }

        return "Titolo: " + title() + '\n' +
               "Contenuto: " + content() + '\n' +
               "Voti: " + rating.pos() + " positivi, " + rating.neg() + " negativi" + '\n' +
               "Commenti:\n" + formattedComments;
    }
    /**
     * @return formatted post as String, adding the author to distinguish rewinned posts in blog
     */
    public String formatR(){
        return "Autore: " + author() + '\n' + format();
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
    public Collection<String> postCurators() {
        List<String> curators = new ArrayList<>();
        for (Rating rating: ratings){
            //curators are the users that left a like
           if (rating.rate() > 0) curators.add(rating.user());
        }
        return curators;
    }
}
