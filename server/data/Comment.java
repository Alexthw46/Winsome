package server.data;

import server.ServerMain;

import java.util.UUID;

/**
 * @param userId id of the comment author
 * @param content body of the comment
 * @param timestamp when the comment was made, in milliseconds
 */
public record Comment(UUID userId, String content, long timestamp) {

    public String format(){
        return "* " + ServerMain.userMap.get(userId()).username() + " : " + content() + '\n';
    }

}
