package server.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import server.IWinImpl;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

@JsonIgnoreProperties(ignoreUnknown = true)
public record User(String username, String password, Set<String> tags, CopyOnWriteArraySet<Post> blog,
                   CopyOnWriteArraySet<String> followedUsers,
                   CopyOnWriteArrayList<Transaction> wallet) {

    //for registration only
    public User(String username, String password, Set<String> tags) {
        this(username, password, tags, new CopyOnWriteArraySet<>(), new CopyOnWriteArraySet<>(), new CopyOnWriteArrayList<>());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof User user && this.username.equals(user.username());
    }

    public synchronized void followUser(String newFollower) {
        this.followedUsers.add(newFollower);
    }

    public synchronized void unfollowUser(String oldFollower) {
        this.followedUsers.remove(oldFollower);
    }

    /**
     * Overridden record getter to filter out removed posts from the blog
     */
    @Override
    public synchronized CopyOnWriteArraySet<Post> blog() {
        blog.removeIf(p -> !this.username.equals(p.author()) && !IWinImpl.postLookup.containsKey(p.postId()));
        return blog;
    }

    //Getter used only to restore data, when the post lookup is not filled yet
    public Set<Post> blogUnchecked() {
        return blog;
    }

    public float getTotalWallet() {
        float counter = 0;
        for (Transaction t : wallet()) {
            counter += t.reward();
        }
        return counter;
    }

    public record Transaction(float reward, long timestamp) {
        @Override
        public String toString() {
            return String.format("%s : Rewarded %.2f Wincoins", new Timestamp(timestamp), reward());
        }
    }
}
