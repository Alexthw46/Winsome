package server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

public interface IWin extends Callable<String> {

    /**
     * use: login <username> <password>
     *
     * @param username username
     * @param password password
     * @return string serializing a Triplet with the userToken and the coordinates for multicast, or a Triplet containing a null userToken and the error message
     */
    String login(String username, String password);

    /**
     * use: list users
     *
     * @param token id of the requesting user
     * @return list of users with at least one common tag with the user
     */
    String listUsers(UUID token);

    /**
     * use: list following
     *
     * @param token id of the user to check
     * @return list of followed users
     */
    Set<UUID> listFollowing(UUID token);

    /**
     * use: follow <user>
     *
     * @param idToFollow id of the user to follow
     * @param idFollower if of the user that wants to follow
     * @return result message
     */
    String followUser(UUID idToFollow, UUID idFollower);

    /**
     * use: follow <user>
     *
     * @param idToUnfollow id of the user to follow
     * @param idFollower   if of the user that wants to follow
     * @return result message
     */
    String unfollowUser(UUID idToUnfollow, UUID idFollower);

    /**
     * use: blog
     *
     * @param userToken id of the requesting user
     * @return list of all the post of the user, formatted
     */
    String viewBlog(UUID userToken);

    /**
     * use: post <title> <content>
     *
     * @param author  author
     * @param title   title
     * @param content content
     * @return formatted post or fail
     */
    String createPost(UUID author, String title, String content);

    /**
     * use: show feed
     *
     * @param userToken id of the user requesting feed
     * @return list of post of the followed users
     */
    String showFeed(UUID userToken);

    /**
     * use: show post <id>
     *
     * @param idPost id of the post to show
     * @return formatted post or error message
     */
    String showPost(int idPost);

    /**
     * use: delete <id>
     *
     * @param token  accessToken of the user requesting to delete
     * @param idPost id of the post to delete
     * @return success or failure message
     */
    String deletePost(UUID token, int idPost);

    /**
     * use: rewin <id>
     *
     * @param token  token of the user that want to rewin
     * @param idPost id of the post to rewin
     * @return result message
     */
    String rewinPost(UUID token, int idPost);

    /**
     * use: rate <id> <voto>
     *
     * @param idPost id of the post to rate
     * @param rate   rating to add
     * @return result message
     */
    String ratePost(int idPost, UUID user, int rate);

    /**
     * * use: content <idPost> <content>
     *
     * @param postId  id of the post to content
     * @param content content of the comment to add
     * @param token   user access token
     * @return result message
     */
    String addComment(int postId, String content, UUID token);

    /**
     * use: wallet
     *
     * @param token requesting user
     * @return how many wincoins the user have
     */
    String getWallet(UUID token);

    /**
     * use: wallet btc
     *
     * @param token requesting user
     * @return wallet points converted into btc
     */
    String getWalletInBitcoin(UUID token);

}
