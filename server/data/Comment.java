package server.data;

import server.ServerMain;

import java.util.UUID;

public record Comment(UUID userId, String content, long timestamp) {

    public String format(){
        return "* " + ServerMain.userMap.get(userId()).username() + " : " + content() + '\n';
    }

}
