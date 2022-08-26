package server;

import common.Triplet;
import common.ClientProxy;
import common.ServerProxy;
import server.data.User;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static server.data.TextValidator.validatePassword;
import static server.data.TextValidator.validateUsername;
import static server.ServerMain.userIdLookup;
import static server.ServerMain.userMap;

public class ServerProxyImpl extends RemoteServer implements ServerProxy {

    /**
     * use: register <username> <password> <tags>
     *
     * @return triplet with <userToken multicastPort multicastAddress>, userToken is null if registration fails
     */
    @Override
    public synchronized Triplet register(String username, String password, String... tagList) {
        UUID id = null;
        if (!userIdLookup.containsKey(username) && validatePassword(password) && validateUsername(username)) {
            id = UUID.randomUUID();
            User newUser = new User(id, username, password, Arrays.stream(tagList).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet()));
            userIdLookup.put(username, id);
            userMap.put(id, newUser);
            ServerMain.logger.add("Registered new user: " + username);
        }
        return new Triplet(ServerMain.config.MulticastAddress(), ServerMain.config.UDPPort(), id);

    }

    /* mappa dei client registrati per il callback */
    private final Map<UUID, ClientProxy> clients;

    public ServerProxyImpl() throws RemoteException {
        clients = new ConcurrentHashMap<>();
    }

    /* registrazione per il callback e inizializzazione cache followers*/
    public synchronized void registerForCallback(UUID token, ClientProxy callbackClient) throws RemoteException {
        if (!clients.containsKey(token)) {
            clients.put(token, callbackClient);
            tryNotifyFollowersUpdate(token);
            System.out.println("New client registered for callbacks.");
        } else throw new RemoteException("Client already registered.");
    }

    /* annulla registrazione per il callback */
    public synchronized void unregisterForCallback(UUID token) throws RemoteException {
        if (clients.remove(token) != null) {
            System.out.println("Client unregistered");
        } else {
            System.out.println("Unable to unregister client.");
        }
    }

    @Override
    public synchronized void tryNotifyFollowersUpdate(UUID toUpdate) throws RemoteException {
        //se il client da notificare è registrato per il callback, la sua cache dei followers viene aggiornata
        if (clients.containsKey(toUpdate)) {
            List<String> followers = ServerMain.getFollowersFromUser(toUpdate);
            clients.get(toUpdate).updateFollowersCache(followers);
        }
    }

}
