package client;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.*;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.*;

public class ClientMain {

    public static boolean isListeningMulticast = false;

    public static InetSocketAddress multicastGroup = null;

    public static void main(String[] args) {

        //read the configs from local file or generate a default one
        ClientConfigHandler.ClientConfig config = ClientConfigHandler.loadConfigs();

        try (Scanner scanner = new Scanner(System.in)) {

            UUID authToken = null;
            boolean shutdown = false;
            List<String> followersCache = new CopyOnWriteArrayList<>();
            ClientProxyImpl clientProxy = new ClientProxyImpl(followersCache);
            ServerProxy serverProxy;

            //connect to RMI register to retrieve proxy
            try {
                Registry r = LocateRegistry.getRegistry(config.RegHost(),config.RegPort());
                serverProxy = (ServerProxy) r.lookup("AUTHENTICATOR");
            } catch (NotBoundException | RemoteException e) {
                System.out.println("Remote Connection to Server refused");
                return;
            }

            //setup tcp address of the server from the configs
            SocketAddress address = new InetSocketAddress(config.ServerAddress(), config.TCPPort());

            //declare multicast group, executor and future message
            MulticastSocket group = null;
            ExecutorService multicastExecutor = Executors.newSingleThreadExecutor();
            Future<String> multicastMessage = null;

            try (SocketChannel serverChannel = SocketChannel.open(address)) {

                if (!serverChannel.isConnected()) {
                    while (!serverChannel.finishConnect()) {
                        System.out.println("Still connecting...");
                    }
                }

                System.out.println("Client ready! Type help for available commands. Login or register to authenticate, shutdown or logout to exit.");
                while (!shutdown) {

                    //try join the multicast group with the data got by logging/registering
                    try {
                        if (group == null && authToken != null && multicastGroup != null) {
                            group = new MulticastSocket(multicastGroup.getPort());
                            NetworkInterface netIf = NetworkInterface.getByInetAddress(multicastGroup.getAddress());
                            group.joinGroup(multicastGroup, netIf);
                        }
                    } catch (IOException e) {
                        System.out.println("Error while connecting to multicast group");
                        throw new RuntimeException();
                    }

                    //if listening, check if a message have been received and print it
                    if (isListeningMulticast && multicastMessage != null && multicastMessage.isDone()) {
                        String message = multicastMessage.get();
                        isListeningMulticast = false;
                        System.out.println("*  " + message + "   *");
                    }

                    //if not listening, creates a task with future result
                    if (!isListeningMulticast && group != null) {
                        MulticastSocket finalGroup = group;
                        multicastMessage = multicastExecutor.submit(() -> listenMulticast(finalGroup));
                        isListeningMulticast = true;
                    }

                    //get the user input
                    Triplet tri = CommandParser.getAndParseCommand(scanner, authToken);
                    int op = tri.op();

                    //block login & register if the user is already authenticated
                    if ((op == 100 || op == -100) && authToken != null) {
                        System.out.println("An user is already logged in, logout first.");
                        continue;
                    }

                    try {
                        //execute the command given
                        switch (op) {
                            case -1 -> CommandParser.printHelp();
                            case -2 -> System.out.println("Invalid operation : " + tri.args());
                            case -3 -> System.out.println("Not enough arguments for operation : " + tri.args());
                            case 0 -> {
                                //start logout
                                shutdown = true;
                                if (authToken != null) {
                                    logout(authToken, serverProxy, tri, serverChannel);
                                    authToken = null;
                                }
                            }
                            case 2 -> {
                                if (authToken != null) //use the local data
                                    clientProxy.listFollowers();
                                else System.out.println("You need to authenticate first.");
                            }
                            case 100 -> {
                                Triplet result = tryLogin(tri, serverChannel); //connect with the server through TCP
                                if (result != null && result.token() != null) {
                                    authToken = result.token(); //save userToken
                                    multicastGroup = new InetSocketAddress(result.args(), result.op()); //save multicast coordinates
                                    serverProxy.registerForCallback(authToken, (ClientProxy) UnicastRemoteObject.exportObject(clientProxy, 0));
                                    System.out.println("Login Successful");
                                } else {
                                    //if token is null, login failed and error message is in args
                                    System.out.println("Login Failed.");
                                    if (result != null) System.out.println(result.args());
                                }
                            }
                            case -100 -> {
                                Triplet result = tryRegister(tri, serverProxy); //connect with the server through RMI
                                if (result != null && result.token() != null) {
                                    authToken = result.token(); //save userToken
                                    multicastGroup = new InetSocketAddress(result.args(), result.op()); //save multicast coordinates
                                    serverProxy.registerForCallback(authToken, (ClientProxy) UnicastRemoteObject.exportObject(clientProxy, 0));
                                    System.out.println("Registration Successful");
                                } else { //if result is null, registration failed
                                    System.out.println("Registration Failed.");
                                }
                            }
                            default -> System.out.println(sendOpToServerNIO(tri, serverChannel)); //send the command to the server through TCP
                        }
                    } catch (IOException e) {
                        shutdown = true;
                        System.out.println("Error while connecting with the Server.");
                        e.printStackTrace();
                    }
                }
            }

            //unload resources after the shutdown starts

            if (group != null) group.close();
            multicastExecutor.shutdown();

            try {
                //Make sure the remote will be unloaded and stop requesting
                UnicastRemoteObject.unexportObject(clientProxy, true);
                clientProxy = null;
                System.gc();
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private static void logout(UUID authToken, ServerProxy serverProxy, Triplet tri, SocketChannel serverChannel) throws IOException {
        serverProxy.unregisterForCallback(authToken);
        sendOpToServerNIO(tri, serverChannel);
        System.out.println("Logging out from account");
    }

    private static String sendOpToServerNIO(Triplet instruction, SocketChannel serverChannel) throws IOException {

        //if the user is not logged in yet, they can only log in from here
        if (instruction.token() == null && instruction.op() != 100) {
            return "You need to authenticate first.";
        }

            //Serialize the triplet to json string to send it
            byte[] toSend = new ObjectMapper().writeValueAsString(instruction).getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(toSend.length);

            buffer.put(toSend);
            buffer.flip();
            int byteWritten = serverChannel.write(buffer);

            while (byteWritten < toSend.length) {
                byteWritten += serverChannel.write(buffer);
            }

            //switch to read the response if needed
            if (instruction.op() > 0) {

                //read the size of the incoming response
                ByteBuffer sizeBuf = ByteBuffer.allocate(Integer.BYTES);
                serverChannel.read(sizeBuf);
                sizeBuf.flip();
                int bytesToRead = sizeBuf.getInt();

                //read the response from the server
                String response = "";
                buffer = ByteBuffer.allocate(bytesToRead);
                int bytesRead = 0;
                while (bytesToRead > 0) {
                    bytesRead = serverChannel.read(buffer);
                    bytesToRead =- bytesRead;
                    buffer.flip();
                    byte[] toConvert = new byte[bytesRead];
                    buffer.get(toConvert);
                    String received = new String(toConvert, StandardCharsets.UTF_8).trim();
                    response = response.concat(received);
                    buffer.clear();
                }
                return response;
            }

        return "";
    }

    public static Triplet tryLogin(Triplet input, SocketChannel serverChannel) throws IOException {
        String result = sendOpToServerNIO(input, serverChannel);
        if (result != null && !result.isBlank()) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(result, Triplet.class);
        }
        return null;
    }

    public static Triplet tryRegister(Triplet input, ServerProxy proxy) throws RemoteException {
        Triplet result = null; //result is a triplet with userToken and multicast coordinates
        String[] tokens = input.args().split(" "); // tokenize the input into author password and tags

        if (tokens.length > 2) {
            //check if the number of tags is correct and call the RMI method
            String[] tags = input.args().substring(1 + tokens[0].length() + tokens[1].length()).trim().toLowerCase().split(" ");
            if (tags.length >= 1 && tags.length <= 5) {
                result = proxy.register(tokens[0], tokens[1], tags);
            } else System.out.println("You need to choose at least one tag, up to five");
        } else {
            System.out.println("Format error");
        }

        return result;
    }

    public static String listenMulticast(MulticastSocket group) {

        byte[] buf = new byte[128];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);

        //start listen on the multicast group
        try {
            group.receive(dp);
        } catch (IOException e) {
            return "Error while listening for wallet updates";
        }

        return new String(dp.getData()).trim();
    }
}
