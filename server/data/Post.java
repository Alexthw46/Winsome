package server.data;


import java.util.*;

public record Post(String username, int postId, String title, String content, List<Comment> comments, Set<Rating> ratings, NumberWrapper<Integer> timesChecked) implements Comparable<Post>{

    public Post(String userId, int postId, String title, String content){
        this(userId, postId, title, content, new ArrayList<>(), new HashSet<>(), new NumberWrapper<>(0));
    }

    public synchronized void rate(UUID user, int voto) {
        this.ratings.add(new Rating(user, voto, System.currentTimeMillis()));
    }

    public synchronized void comment(Comment comment){
        this.comments().add(comment);
    }

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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Post post && post.postId() == this.postId();
    }

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

    @Override
    public int compareTo(Post toCompare) {
        return Integer.compare(this.postId(), toCompare.postId());
    }

    public Collection<UUID> postCurators() {
        List<UUID> curators = new ArrayList<>();
        for (Rating rating: ratings){
           if (rating.rate() > 0) curators.add(rating.user());
        }
        return curators;
    }
}
