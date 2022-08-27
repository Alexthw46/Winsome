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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static server.ServerMain.*;
import static server.data.TextValidator.*;

public class IWinImpl implements IWin {

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
            if (userIdLookup.containsKey(username)) {
                UUID id = userIdLookup.get(username);
                User loginAttempt = userMap.get(id);
                if (loginAttempt.password().equals(password)) {
                    logger.add("User " + username + " logged in.");
                    result = mapper.writeValueAsString(new Triplet(config.MulticastAddress(), config.UDPPort(), id));
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
    public String listUsers(UUID token) {
        String list = (String.format("User %12c Tags \n", '|'));
        User u = userMap.get(token);
        for (User s : userMap.values()) {
            if (!token.equals(s.userId()) && u.tags().stream().anyMatch(i -> s.tags().contains(i)))
                list = list.concat(String.format("* %s %10c %s\n", s.username(), '|', s.tags()));
        }
        return list;
    }

    @Override
    public Set<UUID> listFollowing(UUID token) {
        User user = userMap.get(token);
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
    public String followUser(UUID idToFollow, UUID idFollower) {
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
     * @param idToUnfollow id of the user to follow
     * @param idFollower   if of the user that wants to follow
     * @return result message
     */
    @Override
    public String unfollowUser(UUID idToUnfollow, UUID idFollower) {
        User newFollower = userMap.getOrDefault(idFollower, null);
        User toFollow = userMap.getOrDefault(idToUnfollow, null);
        if (newFollower != null && toFollow != null && !idToUnfollow.equals(idFollower)) {
            newFollower.unfollowUser(idToUnfollow);
            try {
                proxy.tryNotifyFollowersUpdate(idToUnfollow);
            } catch (RemoteException e) {
                logger.add("Failed to notify follower update to client : " + idToUnfollow);
            }
            return "Not following " + toFollow.username() + " anymore.\n";
        } else {
            return "User not found.\n";
        }
    }

    /**
     * use: blog
     *
     * @param userToken id of the requesting user
     * @return list of all the post of the user, formatted
     */
    @Override
    public String viewBlog(UUID userToken) {
        User currentUser = userMap.getOrDefault(userToken, null);
        String blog = "";
        if (currentUser != null) {
            for (Post post : currentUser.blog()) {
                blog = blog.concat(post.format());
            }
        }
        return blog;
    }

    /**
     * use: post <title> <content>
     *
     * @param author  author
     * @param title   title
     * @param content content
     * @return formatted post or fail
     */
    @Override
    public String createPost(UUID author, String title, String content) {
        User user = userMap.getOrDefault(author, null);
        if (user != null && TextValidator.validatePostTitle(title) && TextValidator.validatePostContent(content)) {
            Post newPost = new Post(user.username(), getNewPostId(), title, content);
            user.blog().add(newPost);
            postLookup.put(newPost.postId(), newPost);
            return newPost.format();
        }
        return "failed";
    }

    static int postCounter = 0;

    private synchronized static int getNewPostId() {
        postCounter++;
        return postCounter;
    }

    /**
     * use: show feed of the requesting user
     *
     * @param token id of the requesting user
     * @return feed
     */
    @Override
    public String showFeed(UUID token) {
        List<Post> feedList = new ArrayList<>();

        for (User user : listFollowing(token).stream().map(userMap::get).toList()) {
            feedList.addAll(user.blog());
        }
        feedList.sort(null);

        String feed = String.format("Id %5c Author %5c Title\n", '|', '|');

        for (Post post : feedList) {
            feed = feed.concat(String.format("%s %5c %s %5c %s\n", post.postId(), '|', post.username(), '|', post.title()));
        }

        return feed;
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
     * @param token  accessToken of the user requesting to delete
     * @param idPost id of the post to delete
     * @return success or failure message
     */
    @Override
    public String deletePost(UUID token, int idPost) {
        User user = userMap.get(token);
        if (postLookup.containsKey(idPost) && postLookup.get(idPost).username().equals(user.username())) {
            postLookup.remove(idPost);
            user.blog();
            logger.add(postLookup.get(idPost).format() + "\n Deleted Successfully");
        } else {
            return "Post does not exist or permission was denied";
        }
        return "Successfully removed";
    }

    /**
     * use: rewin <id>
     *
     * @param token  token of the user that want to rewin
     * @param idPost id of the post to rewin
     * @return result message
     */
    @Override
    public String rewinPost(UUID token, int idPost) {

        if (postLookup.containsKey(idPost)) {
            Post toRewin = postLookup.get(idPost);
            User user = userMap.get(token);
            if (!user.username().equals(toRewin.username()) && user.blog().add(toRewin)) {
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
    public String ratePost(int idPost, UUID user, int rate) {
        Post post = postLookup.getOrDefault(idPost, null);
        if (post != null && !userIdLookup.get(post.username()).equals(user)) {
            if (post.rate(user, rate)) {
                return "Success";
            }else return "Already rated this post.";
        }
        return "Post not found or permission denied.";
    }


    /**
     * * use: content <idPost> <content>
     *
     * @param postId  id of the post to content
     * @param content content to add
     * @param token   user access token
     * @return result message
     */
    @Override
    public String addComment(int postId, String content, UUID token) {
        Post post = postLookup.getOrDefault(postId, null);
        if (post != null && validateComment(content)) {
            Comment comment = new Comment(token, content, System.currentTimeMillis());
            post.comment(comment);
            return comment.format();
        }
        return "Post id not found.";
    }

    /**
     * use: wallet
     *
     * @param token requesting user
     * @return how many wincoins the user have
     */
    @Override
    public String getWallet(UUID token) {
        return String.format("%.2f Wincoins.\n", userMap.get(token).wallet().getValue());
    }

    /**
     * use: wallet btc
     *
     * @param token requesting user
     * @return wallet points converted into btc
     */
    @Override
    public String getWalletInBitcoin(UUID token) {
        float winCoins = userMap.get(token).wallet().getValue();
        float conversionRatio = 0;

        String randomOrg = "https://www.random.org/decimal-fractions/?num=1&dec=10&col=1&format=plain&rnd=new";

        //send a http request to RANDOM.ORG to simulate coin value fluctuation
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI(randomOrg)).GET().build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
        switch (this.input.op()) {
            case 100 -> {
                String[] tokens = input.args().split(" ");
                return login(tokens[0], tokens[1]);
            }
            case 1 -> {
                return listUsers(input.token());
            }
            case 3 ->{
                return listFollowing(input.token()).toString();
            }
            case 4 -> {
                logger.add("follow request for " + input.args());
                return followUser(userIdLookup.get(input.args()), input.token());
            }
            case 5 -> {
                logger.add("unfollow request for " + input.args());
                return unfollowUser(userIdLookup.get(input.args()), input.token());
            }
            case 6 -> {
                return viewBlog(input.token());
            }
            case 10 -> {
                List<String> matches = pattern.matcher(input.args())
                        .results()
                        .map(mr -> mr.group(1)).toList();
                if (matches.size() == 2) {
                    String title = matches.get(0);
                    String post = matches.get(1);
                    return createPost(input.token(), title, post);
                } else {
                    return "Error while creating post, make sure title and contents are between \" ";
                }
            }
            case 11 -> {
                if (isNumeric(input.args()))
                    return deletePost(input.token(), Integer.parseInt(input.args()));
            }
            case 12 -> {
                if (isNumeric(input.args()))
                    return rewinPost(input.token(), Integer.parseInt(input.args()));
            }
            case 13 -> {
                String[] split = input.args().split("\"");
                if (isNumeric(split[0].trim()) && split.length > 1) {
                    int postId = Integer.parseInt(split[0].trim());
                    String comment = split[1].trim();
                    return addComment(postId, comment, input.token());
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
                    return ratePost(postId, input.token(), rate);
                } catch (NumberFormatException e) {
                    return "Arguments error, not a number.";
                }
            }
            case 20 -> {
                return showFeed(input.token());
            }
            case 21 -> {
                if (isNumeric(input.args())) {
                    return showPost(Integer.parseInt(input.args()));
                }else return "Arguments error, not a number.";
            }
            case 30 -> {
                return getWallet(input.token());
            }
            case 31 -> {
                return getWalletInBitcoin(input.token());
            }
        }
        return "No operation found for this request";
    }

}
