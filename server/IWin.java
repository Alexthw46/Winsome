package server;

import java.util.Set;
import java.util.concurrent.Callable;

public interface IWin extends Callable<String> {

    /**
     * use: login <author> <password>
     *
     * @param username username
     * @param password password
     * @return string serializing a Triplet with the userToken and the coordinates for multicast, or a Triplet containing a null userToken and the error message
     */
    String login(String username, String password);

    /**
     * use: list users
     *
     * @param username of the requesting user
     * @return list of users with at least one common tag with the user
     */
    String listUsers(String username);

    /**
     * use: list following
     *
     * @param username of the user to check
     * @return list of followed users
     */
    Set<String> listFollowing(String username);

    /**
     * use: follow <user>
     *
     * @param idToFollow username of the user to follow
     * @param idFollower username of the user that wants to follow
     * @return result message
     */
    String followUser(String idToFollow, String idFollower);

    /**
     * use: follow <user>
     *
     * @param userToUnfollow username of the user to unfollow
     * @param idFollower     username of the user that wants to stop follow
     * @return result message
     */
    String unfollowUser(String userToUnfollow, String idFollower);

    /**
     * use: blog
     *
     * @param username of the requesting user
     * @return list of all the post of the user, formatted
     */
    String viewBlog(String username);

    /**
     * use: post <title> <content>
     *
     * @param author  author
     * @param title   title
     * @param content content
     * @return formatted post or fail
     */
    String createPost(String author, String title, String content);

    /**
     * use: show feed
     *
     * @param username id of the user requesting feed
     * @return list of post of the followed users
     */
    String showFeed(String username);

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
     * @param username of the user requesting to delete
     * @param idPost id of the post to delete
     * @return success or failure message
     */
    String deletePost(String username, int idPost);

    /**
     * use: rewin <id>
     *
     * @param username of the user that want to rewin
     * @param idPost id of the post to rewin
     * @return result message
     */
    String rewinPost(String username, int idPost);

    /**
     * use: rate <id> <voto>
     *
     * @param idPost id of the post to rate
     * @param username of the rating user
     * @param rate rating to add
     * @return result message
     */
    String ratePost(int idPost, String username, int rate);

    /**
     * * use: content <idPost> <content>
     *
     * @param postId  id of the post to content
     * @param content content of the comment to add
     * @param username  user's author
     * @return result message
     */
    String addComment(int postId, String content, String username);

    /**
     * use: wallet
     *
     * @param username requesting user
     * @return how many wincoins the user have
     */
    String getWallet(String username);

    /**
     * use: wallet btc
     *
     * @param username requesting user
     * @return wallet points converted into btc
     */
    String getWalletInBitcoin(String username);

}
