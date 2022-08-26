package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.UUID;

public interface ServerProxy extends Remote {

    /**
     * use: register <username> <password> <tags>
     *
     * @return triplet with <userToken multicastPort multicastAddress>, userToken is null if registration fails
     */
    Triplet register(String username, String password, String... TagList) throws RemoteException;

    /* registrazione per il callback */
    void registerForCallback(UUID authToken, ClientProxy callbackClient) throws RemoteException;

    /* cancella registrazione per il callback */
    void unregisterForCallback(UUID authToken) throws RemoteException;

    /* callback per aggiornare la cache dei followers */
    void tryNotifyFollowersUpdate(UUID toUpdate) throws RemoteException;

}
