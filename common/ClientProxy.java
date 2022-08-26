package common;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientProxy extends Serializable, Remote {

    void updateFollowersCache(List<String> followers) throws RemoteException;

    /**
     * use: list followers
     */
    void listFollowers() throws RemoteException;
}
