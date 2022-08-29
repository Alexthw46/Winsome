package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.ServerProxy;
import common.Triplet;
import server.data.Post;
import server.data.User;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class ServerMain {

    //Lookup map to get the userToken from the username
    public static final Map<String, UUID> userIdLookup = new ConcurrentHashMap<>();

    //Map that index Users with their userToken
    public static final Map<UUID, User> userMap = new ConcurrentHashMap<>();

    //map of all the posts, indexed by their postId
    public static final Map<Integer, Post> postLookup = new ConcurrentHashMap<>();


    public static ServerConfig config;

    //Thread pool for executing requests
    static final ExecutorService pool = Executors.newCachedThreadPool();

    //Time of the last check of rewards
    static long lastCheck = System.currentTimeMillis();

    static List<String> logger = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {

        //read local files to restore data, create default or read config files
        if (PersistentDataManager.initialize())
            System.out.println("Server data recovered.");
        else {
            System.out.println("Error while restoring saved data.");
            return;
        }

        //Set up the proxy for clients registration
        ServerProxy proxy;
        try {
            proxy = new ServerProxyImpl();
            ServerProxy stub = (ServerProxy) UnicastRemoteObject.exportObject(proxy, 0);
            LocateRegistry.createRegistry(config.RegPort());
            Registry reg = LocateRegistry.getRegistry(config.ServerAddress(), config.RegPort());
            reg.rebind(config.RegHost(), stub);
        } catch (RemoteException e) {
            System.out.println("Error setting up Remote registry");
            e.printStackTrace();
            return;
        }

        System.out.println("Server ready");

        ObjectMapper mapper = new ObjectMapper();
        ServerSocketChannel serverSocketChannel;
        Selector selector;

        try {
            serverSocketChannel = ServerSocketChannel.open();
            selector = Selector.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(config.ServerAddress(), config.TCPPort()));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            System.out.println("Error setting up Selector");
            e.printStackTrace();
            return;
        }

        //map of future results for each request
        Map<Integer, Future<String>> outMap = new ConcurrentHashMap<>();

        boolean shutdown = false;
        try (DatagramSocket multicastSocket = new DatagramSocket()) {

            while (!shutdown) {

                //award points and save timestamp of last check
                if (System.currentTimeMillis() - lastCheck > config.PointsAwardInterval() * 1000) {
                    calculatePointsAndAward(multicastSocket, lastCheck);
                    lastCheck = System.currentTimeMillis();
                }

                //try to select keys, blocking op with timeout to let the points award check run
                try {
                    selector.select(config.selectTimeout());
                } catch (IOException ex) {
                    ex.printStackTrace();
                    break;
                }

                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    try {
                        //accept connection and allocate buffer
                        if (key.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel client = server.accept();
                            client.configureBlocking(false);

                            //prepare to read, wrap buffer with channelId
                            BufferWrapper readBuffer = new BufferWrapper(getNextSelectorId(), 128);
                            client.register(selector, SelectionKey.OP_READ, readBuffer);

                        }
                        //read request and prepare future task
                        if (key.isReadable()) {
                            SocketChannel client = (SocketChannel) key.channel();
                            BufferWrapper wrapper = ((BufferWrapper) key.attachment());
                            ByteBuffer buffer = wrapper.getBuffer();
                            int selectorId = wrapper.getId();

                            //read msg
                            int bytesRead = client.read(buffer);
                            String msg = "";
                            while (bytesRead > 0) {
                                buffer.flip();
                                byte[] toConvert = new byte[bytesRead];
                                buffer.get(toConvert);
                                String received = new String(toConvert, StandardCharsets.UTF_8).trim();
                                msg = msg.concat(received);
                                buffer.clear();
                                bytesRead = client.read(buffer);
                            }
                            //deserialize string to input triplet
                            Triplet triplet = mapper.readValue(msg, Triplet.class);

                            //special case for logout and shutdown
                            if (triplet.op() == 0) {
                                client.close();
                                outMap.remove(selectorId);
                                key.cancel();
                                System.out.println("Connection with client closed");
                                if (triplet.args().equals("shutdown")) {
                                    shutdown = true;
                                    pool.shutdown();
                                }
                                continue;
                            } else {
                                //execute user operation
                                String message = triplet.op() + " | args: " + triplet.args() + " da: " + triplet.token() + '\n';
                                logger.add("Executing : " + message);

                                checkAndExecute(selectorId, outMap, triplet, proxy);
                            }

                            //switch to write
                            client.register(selector, SelectionKey.OP_WRITE, wrapper);

                        } else if (key.isWritable()) {

                            SocketChannel client = (SocketChannel) key.channel();
                            BufferWrapper wrapper = ((BufferWrapper) key.attachment());
                            int selectorId = wrapper.getId();

                            // send the result back to client if ready
                            try {
                                if (outMap.containsKey(selectorId) && outMap.get(selectorId).isDone()) {
                                    String out = outMap.get(selectorId).get();
                                    byte[] toEcho = out.getBytes(StandardCharsets.UTF_8);

                                    //send the length of the message
                                    ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
                                    length.putInt(toEcho.length);
                                    length.flip();
                                    client.write(length);
                                    length.clear();

                                    //send the message
                                    ByteBuffer buffer = wrapper.newBuffer(toEcho.length);
                                    buffer.clear();
                                    buffer.put(toEcho);
                                    buffer.flip();
                                    client.write(buffer);

                                    //back to read
                                    buffer.clear();
                                    client.register(selector, SelectionKey.OP_READ, wrapper);
                                }
                            } catch (ExecutionException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (IOException e) {
                        key.cancel();
                    }
                    iterator.remove();
                }
            }
            selector.close();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            PersistentDataManager.saveAll();
        }

        //make sure the remote object is unloaded
        try {
            UnicastRemoteObject.unexportObject(proxy, true);
            proxy = null;
            System.gc();
        } catch (Exception ignored) {}

    }


    /**
     * @param requestId id of the current request
     * @param outMap map <id futureResult>
     * @param tri triplet with the user input
     * @param proxy remote object to make callbacks to client
     */
    private static void checkAndExecute(int requestId, Map<Integer, Future<String>> outMap, Triplet tri, ServerProxy proxy) {
        IWin worker = new IWinImpl(tri, proxy);
        try {
            outMap.put(requestId, pool.submit(worker));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getFollowersFromUser(UUID userId) {
        String username = userMap.get(userId).username();
        List<String> followers = new ArrayList<>();
        for (User user : userMap.values()) {
            if (user.followedUsers().contains(username)) {
                followers.add(user.username());
            }
        }
        return followers;
    }

    static int selectorCounter = 1;

    public static int getNextSelectorId() {
        selectorCounter++;
        return selectorCounter;
    }

    static final String winCoinUpdateNotify = "WinCoins awarded\n";

    public static void calculatePointsAndAward(DatagramSocket group, long startTime) throws IOException {

        //for each post award points to author and curators
        for (Post p : postLookup.values()) {
            List<RewardsCalculator.CoinReward> rewards = RewardsCalculator.getPointsFromPost(p, startTime, config.AuthorReward());
            for (RewardsCalculator.CoinReward coins : rewards) {
                User user = userMap.get(coins.user());
                user.wallet().setValue(user.wallet().getValue() + coins.reward());
                logger.add(user.username() + " earned " + coins.reward() + " from post n. " + p.postId() + '\n');
            }
        }
        //create udp packet to send through multicast to notify clients
        InetAddress address = InetAddress.getByName(config.MulticastAddress());
        byte[] buffer = winCoinUpdateNotify.getBytes();
        DatagramPacket dp = new DatagramPacket(buffer, buffer.length, address, config.UDPPort());
        System.out.println(winCoinUpdateNotify);
        logger.add(winCoinUpdateNotify + " at: " + System.currentTimeMillis());
        group.send(dp);

    }

}

