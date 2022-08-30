package server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.Triplet;
import common.ServerProxy;
import server.data.Comment;
import server.data.Post;
import server.data.TextValidator;
import server.data.User;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static server.ServerMain.*;
import static server.data.TextValidator.*;

public class IWinImpl implements IWin {

    //Lookup map to get the username from the userToken
    public static final Map<UUID, String> userIdLookup = new ConcurrentHashMap<>();
    //Map that index Users with their usename
    public static final Map<String, User> userMap = new ConcurrentHashMap<>();
    //map of all the posts, indexed by their postId
    public static final Map<Integer, Post> postLookup = new ConcurrentHashMap<>();
    final Triplet input;
    final ServerProxy proxy;

    public IWinImpl(Triplet tri, ServerProxy proxy) {
        input = tri;
        this.proxy = proxy;
    }

    /**
     * use: login <username> <password>
     *
     * @param username username
     * @param password password
     * @return string serializing a Triplet with the userToken and the coordinates for multicast, or a Triplet containing a null userToken and the error message
     */
    @Override
    public String login(String username, String password) {
        ObjectMapper mapper = new ObjectMapper();
        String result = "";
        try {
            if (userMap.containsKey(username)) {
                UUID id = UUID.randomUUID();
                User loginAttempt = userMap.get(username);
                if (loginAttempt.password().equals(password)) {
                    logger.add("User " + username + " logged in.");
                    result = mapper.writeValueAsString(new Triplet(config.MulticastAddress(), config.UDPPort(), id));
                    userIdLookup.put(id, username);
                } else
                    result = mapper.writeValueAsString(new Triplet("Wrong password.", -1, null));
            } else {
                result = mapper.writeValueAsString(new Triplet("Wrong username or not registered.", -1, null));
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * use: list users
     */
    @Override
    public String listUsers(String username) {
        String list = (String.format("User %12c Tags \n", '|'));
        User u = userMap.get(username);
        for (User s : userMap.values()) {
            if (!u.username().equals(s.username()) && u.tags().stream().anyMatch(i -> s.tags().contains(i)))
                list = list.concat(String.format("* %s %10c %s\n", s.username(), '|', s.tags()));
        }
        return list;
    }

    public static List<String> getUserFollowers(String userId) {
        String username = userMap.get(userId).username();
        List<String> followers = new ArrayList<>();
        for (User user : userMap.values()) {
            if (user.followedUsers().contains(username)) {
                followers.add(user.username());
            }
        }
        return followers;
    }


    @Override
    public Set<String> listFollowing(String username) {
        User user = userMap.get(username);
        return user.followedUsers();
    }

    /**
     * use: follow <user>
     *
     * @param idToFollow id of the user to follow
     * @param idFollower if of the user that wants to follow
     * @return result message
     */
    @Override
    public String followUser(String idToFollow, String idFollower) {
        User newFollower = userMap.getOrDefault(idFollower, null);
        User toFollow = userMap.getOrDefault(idToFollow, null);
        if (newFollower != null && toFollow != null && !idToFollow.equals(idFollower)) {
            newFollower.followUser(idToFollow);
            try {
                proxy.tryNotifyFollowersUpdate(idToFollow);
            } catch (RemoteException e) {
                logger.add("Failed to notify follower update to client : " + idToFollow);
            }
            return "Now following " + toFollow.username();
        } else {
            return "User not found";
        }
    }

    /**
     * use: follow <user>
     *
     * @param userToUnfollow the user to unfollow
     * @param follower   username of the user that wants to stop following
     * @return result message
     */
    @Override
    public String unfollowUser(String userToUnfollow, String follower) {
        User oldFollower = userMap.getOrDefault(follower, null);
        User toUnfollow = userMap.getOrDefault(userToUnfollow, null);
        if (oldFollower != null && toUnfollow != null && !userToUnfollow.equals(follower)) {
            oldFollower.unfollowUser(userToUnfollow);
            try {
                proxy.tryNotifyFollowersUpdate(userToUnfollow);
            } catch (RemoteException e) {
                logger.add("Failed to notify follower update to client : " + userToUnfollow);
            }
            return "Not following " + toUnfollow.username() + " anymore.\n";
        } else {
            return "User not found.\n";
        }
    }

    /**
     * use: blog
     *
     * @param username id of the requesting user
     * @return list of all the post of the user, formatted
     */
    @Override
    public String viewBlog(String username) {
        User currentUser = userMap.getOrDefault(username, null);
        String blog = "";
        if (currentUser != null) {
            for (Post post : currentUser.blog()) {
                if (post.author().equals(username)) {
                    blog = blog.concat(post.format());
                } else {
                    blog = blog.concat(post.formatR());
                }
            }
        }
        return blog;
    }

    /**
     * use: post <title> <content>
     *
     * @param author  author's username
     * @param title   post title
     * @param content post content
     * @return id of the post if success or error message if fail
     */
    @Override
    public String createPost(String author, String title, String content) {
        User user = userMap.getOrDefault(author, null);
        if (user != null && TextValidator.validatePostTitle(title) && TextValidator.validatePostContent(content)) {
            Post newPost = new Post(user.username(), getNewPostId(), title, content);
            user.blog().add(newPost);
            postLookup.put(newPost.postId(), newPost);
            return "Success. New post made - ID: " + newPost.postId();
        }
        return "Create post failed, title or content not valid.";
    }

    //Counter of last postId used, it's updated to the maximum postId when the data is restored.
    static int postCounter = 0;

    private synchronized static int getNewPostId() {
        postCounter++;
        return postCounter;
    }

    /**
     * use: show feed
     *
     * @param username of the requesting user
     * @return formatted feed, listing <post id | post author | post title>
     */
    @Override
    public String showFeed(String username) {
        List<Post> feedList = new ArrayList<>();

        for (User user : listFollowing(username).stream().map(userMap::get).toList()) {
            feedList.addAll(user.blog());
        }
        feedList.sort(null);

        String feed = String.format("Id %5c Author %5c Title\n", '|', '|');

        for (Post post : feedList) {
            feed = feed.concat(String.format("%s %5c %s %5c %s\n", post.postId(), '|', post.author(), '|', post.title()));
        }

        return feed;
    }

    /**
     * @param postId id of the post
     * @param username of the user
     * @return true if the post is present in one of the blog of the users followed by user
     */
    public boolean isInFeed(int postId, String username){
        for (User user : listFollowing(username).stream().map(userMap::get).toList()) {
            for (Post p : user.blog()){
                if (p.postId() == postId) return true;
            }
        }
        return false;
    }


    /**
     * use: show post <id>
     *
     * @param idPost id of the post to show
     * @return formatted post or error message
     */
    @Override
    public String showPost(int idPost) {
        if (postLookup.containsKey(idPost)) {
            return postLookup.get(idPost).format();
        } else {
            return "Post does not exist";
        }
    }

    /**
     * use: delete <id>
     *
     * @param username username of the user requesting to delete
     * @param idPost   id of the post to delete
     * @return success or failure message
     */
    @Override
    public String deletePost(String username, int idPost) {
        User user = userMap.get(username);
        if (postLookup.containsKey(idPost) && postLookup.get(idPost).author().equals(user.username())) {
            Post toDelete = postLookup.remove(idPost);
            user.blog().remove(toDelete);
            logger.add(toDelete.format() + "\n Deleted Successfully");
            return "Successfully removed";
        }
        return "Post does not exist or permission was denied";
    }

    /**
     * use: rewin <id>
     *
     * @param username of the user that want to rewin
     * @param idPost id of the post to rewin
     * @return result message
     */
    @Override
    public String rewinPost(String username, int idPost) {

        if (postLookup.containsKey(idPost)) {
            Post toRewin = postLookup.get(idPost);
            User user = userMap.get(username);
            if (!user.username().equals(toRewin.author()) && user.blog().add(toRewin)) {
                return "Post rewinned to user blog";
            } else {
                return "Post already present in user blog";
            }
        } else return "Post not found";

    }

    /**
     * use: rate <id> <voto>
     *
     * @param idPost id of the post to rate
     * @param rate   rating to add
     * @return result message
     */
    @Override
    public String ratePost(int idPost, String username, int rate) {
        Post post = postLookup.getOrDefault(idPost, null);
        if (post != null && !post.author().equals(username) && isInFeed(idPost, username)) {
            if (post.rate(username, rate)) {
                return "Success";
            }else return "Already rated this post.";
        }
        return "Post not found in your feed or permission denied.";
    }


    /**
     * * use: content <idPost> <content>
     *
     * @param postId  id of the post to content
     * @param content content to add
     * @param username user commenting
     * @return result message
     */
    @Override
    public String addComment(int postId, String content, String username) {
        Post post = postLookup.getOrDefault(postId, null);
        if (post != null && validateComment(content) && isInFeed(postId, username)) {
            Comment comment = new Comment(username, content, System.currentTimeMillis());
            post.comment(comment);
            return comment.format();
        }
        return "Post id not found in your feed.";
    }

    /**
     * use: wallet
     *
     * @param username requesting user
     * @return how many wincoins the user have
     */
    @Override
    public String getWallet(String username) {
        return String.format("%.2f Wincoins.\n", userMap.get(username).wallet().getValue());
    }

    /**
     * use: wallet btc
     *
     * @param username requesting user
     * @return wallet points converted into btc
     */
    @Override
    public String getWalletInBitcoin(String username) {
        float winCoins = userMap.get(username).wallet().getValue();
        float conversionRatio = 0;

        String randomOrg = "https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new";

        //send a http request to RANDOM.ORG to simulate coin value fluctuation
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI(randomOrg)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String toParse = response.body();
                conversionRatio = Float.parseFloat(toParse);
                logger.add("Current conversion ratio: " + response.body());
            } else {
                System.out.println(response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            return "Conversion Service not available";
        }

        float bitcoins = winCoins * conversionRatio;
        return String.format("%.2f Bitcoins\n", bitcoins);
    }

    //pattern to check text between "
    static final Pattern pattern = Pattern.compile("\"(.*?)\"");

    /**
     * Execute the operation corresponding to the opcode given.
     *
     * @return result of operation or error message
     */
    @Override
    public String call() {
        //switch on opcode from the input triplet

        if (input.token() == null || !userIdLookup.containsKey(input.token())) {
            if (this.input.op() == 100){
                String[] tokens = input.args().split(" ");
                return login(tokens[0], tokens[1]);
            }
            return "AuthError";
        }

        String username = userIdLookup.get(input.token());

        switch (this.input.op()) {
            case 1 -> {
                return listUsers(username);
            }
            case 3 ->{
                return listFollowing(username).toString();
            }
            case 4 -> {
                logger.add("follow request for " + input.args());
                return followUser(input.args(), username);
            }
            case 5 -> {
                logger.add("unfollow request for " + input.args());
                return unfollowUser(input.args(), username);
            }
            case 6 -> {
                return viewBlog(username);
            }
            case 10 -> {
                List<String> matches = pattern.matcher(input.args())
                        .results()
                        .map(mr -> mr.group(1)).toList();
                if (matches.size() == 2) {
                    String title = matches.get(0);
                    String post = matches.get(1);
                    return createPost(username, title, post);
                } else {
                    return "Error while creating post, make sure title and contents are between \" ";
                }
            }
            case 11 -> {
                if (isNumeric(input.args()))
                    return deletePost(username, Integer.parseInt(input.args()));
            }
            case 12 -> {
                if (isNumeric(input.args()))
                    return rewinPost(username, Integer.parseInt(input.args()));
            }
            case 13 -> {
                String[] split = input.args().split("\"");
                if (isNumeric(split[0].trim()) && split.length > 1) {
                    int postId = Integer.parseInt(split[0].trim());
                    String comment = split[1].trim();
                    return addComment(postId, comment, username);
                } else {
                    return "Arguments not valid. Type help for how to use.";
                }
            }
            case 14 -> {
                String[] split = input.args().split(" ");
                //split and parse the two arguments or throw error
                try {
                    if (split.length < 2 || !isNumeric(split[0]) || !isNumeric(split[1])) throw new NumberFormatException();

                    int postId = Integer.parseInt(split[0].trim());
                    int rate = Integer.parseInt(split[1].trim());
                    if (Math.abs(rate) != 1) return "Only valid ratings are 1 and -1";
                    return ratePost(postId, username, rate);
                } catch (NumberFormatException e) {
                    return "Arguments error, not a number.";
                }
            }
            case 20 -> {
                return showFeed(username);
            }
            case 21 -> {
                if (isNumeric(input.args())) {
                    return showPost(Integer.parseInt(input.args()));
                }else return "Arguments error, not a number.";
            }
            case 30 -> {
                return getWallet(username);
            }
            case 31 -> {
                return getWalletInBitcoin(username);
            }
        }
        return "No operation found for this request";
    }

}
