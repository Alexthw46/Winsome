package server.data;

import server.ServerMain;

import java.util.*;

public record User(UUID userId, String username, String password, Set<String> tags, Set<Post> blog, Set<UUID> followedUsers, NumberWrapper<Float> wallet) {

    //for registration
    public User(UUID userId, String username, String password, Set<String> tags){
        this(userId, username, password, tags, new HashSet<>(), new HashSet<>(), new NumberWrapper<>(0F));
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

    @Override
    public synchronized Set<Post> blog() {
        blog.removeIf(p -> !ServerMain.postLookup.containsKey(p.postId()));
        return blog;
    }

    //Only used to restore data, when the lookup is not filled yet
    public Set<Post> blogUnchecked(){
        return blog;
    }

}
