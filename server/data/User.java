package server.data;

import server.ServerMain;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public record User(UUID userId, String username, String password, Set<String> tags, CopyOnWriteArraySet<Post> blog, CopyOnWriteArraySet<UUID> followedUsers, NumberWrapper<Float> wallet) {

    //for registration only
    public User(UUID userId, String username, String password, Set<String> tags){
        this(userId, username, password, tags, new CopyOnWriteArraySet<>(), new CopyOnWriteArraySet<>(), new NumberWrapper<>(0F));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof User user && this.userId().equals(user.userId());
    }

    public synchronized void followUser(UUID idFollower) {
        this.followedUsers.add(idFollower);
    }

    public synchronized void unfollowUser(UUID idFollower) {
        this.followedUsers.remove(idFollower);
    }

    /**
     * Overridden record getter to filter out removed posts from the blog
     */
    @Override
    public synchronized CopyOnWriteArraySet<Post> blog() {
        blog.removeIf(p -> !ServerMain.postLookup.containsKey(p.postId()));
        return blog;
    }

    //Getter used only to restore data, when the post lookup is not filled yet
    public Set<Post> blogUnchecked(){
        return blog;
    }

}
