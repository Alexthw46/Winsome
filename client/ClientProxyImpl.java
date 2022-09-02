package client;

import common.ClientProxy;

import java.rmi.server.RemoteObject;
import java.util.List;

public class ClientProxyImpl extends RemoteObject implements ClientProxy {

    public List<String> followersCache;

    public ClientProxyImpl(List<String> clientCache){
        followersCache = clientCache;
    }

    @Override
    synchronized public void updateFollowersCache(List<String> followers) {
        followersCache = followers;
    }

    /**
     * use: list followers
     */
    @Override
    public void listFollowers() {
        System.out.println(followersCache);
    }

}
