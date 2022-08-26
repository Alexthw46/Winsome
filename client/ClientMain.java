package client;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.ClientProxy;
import common.ServerProxy;
import common.Triplet;

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
                Registry r = LocateRegistry.getRegistry(config.RegPort());
                serverProxy = (ServerProxy) r.lookup(config.RegHost());
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

            while (!shutdown) {

                //try join the multicast group with the data got by logging/registering
                try {
                    if (group == null && authToken != null && multicastGroup != null) {
                        group = new MulticastSocket(multicastGroup.getPort());
                        NetworkInterface netIf = NetworkInterface.getByName("walletUpdater");
                        group.joinGroup(multicastGroup, netIf);
                    }
                } catch (IOException e) {
                    System.out.println("Error while connecting to multicast group");
                    throw new RuntimeException();
                }

                //if not listening, creates a task with future result
                if (!isListeningMulticast && group != null) {
                    MulticastSocket finalGroup = group;
                    multicastMessage = multicastExecutor.submit(() -> listenMulticast(finalGroup));
                    isListeningMulticast = true;
                }

                //if listening, check if a message have been received and print it
                if (isListeningMulticast && multicastMessage != null && multicastMessage.isDone()) {
                    String message = multicastMessage.get();
                    isListeningMulticast = false;
                    System.out.println("*  " + message + "   *");
                }

                Triplet tri = CommandParser.getAndParseCommand(scanner, authToken);
                int op = tri.op();

                //block login & register if the user is already authenticated
                if ((op == 100 || op == -100) && authToken != null) {
                    System.out.println("An user is already logged in, logout first.");
                    continue;
                }


                try {

                    //
                    switch (op) {
                        case -1 -> CommandParser.printHelp();
                        case -2 -> System.out.println("Invalid operation : " + tri.args());
                        case -3 -> System.out.println("Not enough arguments for operation : " + tri.args());
                        case 0 -> {
                            //start logout
                            logout(authToken, serverProxy, tri, address);
                            authToken = null;
                            shutdown = true;
                        }
                        case 2 -> clientProxy.listFollowers();
                        case 100 -> {
                            Triplet result = tryLogin(tri, address);
                            if (result != null && result.token() != null) {
                                authToken = result.token();
                                multicastGroup = new InetSocketAddress(result.args(), result.op());
                                serverProxy.registerForCallback(authToken, (ClientProxy) UnicastRemoteObject.exportObject(clientProxy, 0));
                                System.out.println("Login Successful");
                            } else {
                                System.out.println("Login Failed.");
                                if (result != null) System.out.println(result.args());
                            }
                        }
                        case -100 -> {
                            Triplet result = tryRegister(tri, serverProxy);
                            if (result != null && result.token() != null) {
                                authToken = result.token();
                                multicastGroup = new InetSocketAddress(result.args(), result.op());
                                serverProxy.registerForCallback(authToken, (ClientProxy) UnicastRemoteObject.exportObject(clientProxy, 0));
                                System.out.println("Registration Successful");
                            } else {
                                System.out.println("Registration Failed.");
                            }
                        }
                        default -> System.out.println(sendOpToServerNIO(tri, address));
                    }
                } catch (IOException e) {
                    shutdown = true;
                    System.out.println("Error while connecting with the Server");
                    e.printStackTrace();
                }


            }

            try {
                if (group != null) group.close();
                multicastExecutor.shutdown();
                //Make sure the remote will be unloaded and stop requesting
                UnicastRemoteObject.unexportObject(clientProxy, true);
                clientProxy = null;
                System.gc();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    private static void logout(UUID authToken, ServerProxy serverProxy, Triplet tri, SocketAddress address) throws IOException {
        serverProxy.unregisterForCallback(authToken);
        sendOpToServerNIO(tri, address);
        System.out.println("Logging out from account");
    }

    private static String sendOpToServerNIO(Triplet instruction, SocketAddress address) throws IOException {

        if (instruction.token() == null && instruction.op() != 100) {
            return "You need to authenticate first.";
        }

        try (SocketChannel serverChannel = SocketChannel.open()) {

            if (!serverChannel.isConnected()) {
                serverChannel.connect(address);
                while (!serverChannel.finishConnect()) {
                    System.out.println("Connessione non terminata");
                }
            }

            ObjectMapper mapper = new ObjectMapper();

            byte[] toSend = mapper.writeValueAsString(instruction).getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(toSend.length);

            buffer.put(toSend);
            buffer.flip();
            int byteWritten = serverChannel.write(buffer);

            while (byteWritten < toSend.length) {
                byteWritten += serverChannel.write(buffer);
            }

            buffer.clear();

            if (instruction.op() > 0) {
                String response = "";
                int bytesRead = serverChannel.read(buffer);
                while (bytesRead != -1) {
                    buffer.flip();
                    byte[] toConvert = new byte[bytesRead];
                    buffer.get(toConvert);
                    String received = new String(toConvert, StandardCharsets.UTF_8).trim();
                    response = response.concat(received);
                    buffer.clear();
                    bytesRead = serverChannel.read(buffer);
                }
                return response;
            }
        }
        return "";
    }

    public static Triplet tryLogin(Triplet input, SocketAddress address) throws IOException {
        String result = sendOpToServerNIO(input, address);
        if (result != null && !result.isBlank()) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(result, Triplet.class);
        } else {
            System.out.println(result);
        }
        return null;
    }

    public static Triplet tryRegister(Triplet input, ServerProxy proxy) throws RemoteException {
        Triplet result = null;
        String[] tokens = input.args().split(" ");

        if (tokens.length > 2) {
            String[] tags = input.args().substring(1 + tokens[0].length() + tokens[1].length()).split(" ");
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

        try {
            group.receive(dp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new String(dp.getData()).trim();
    }
}
