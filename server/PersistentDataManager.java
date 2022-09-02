package server;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import server.data.Post;
import server.data.User;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PersistentDataManager {
    static final ServerConfig defaults = new ServerConfig("localhost", "239.255.32.32", 1080, 44444, "localhost", 1846,  0.75F,100L, 2000L);

    public static boolean initialize() {

        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        File configDir = new File("config");
        File configs = new File("config" + File.separatorChar + "serverConfigs.yaml");

        //load configs from file or create one with the default values
        try {
            if (!configDir.isDirectory()) configDir.mkdir();
            if (configs.createNewFile()) {
                om.writeValue(configs, defaults);
                ServerMain.config = defaults;
            } else {
                ServerMain.config = om.readValue(configs, ServerConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (ServerMain.config.AuthorReward() > 1 || ServerMain.config.AuthorReward() <= 0){
            System.out.println("Error while loading configs, AuthorReward needs to be a decimal between 0 and 1. Current value: " + ServerMain.config.AuthorReward());
            return false;
        }

        JsonFactory factory = new JsonFactory();

        //restores the users and posts from local files

        File savedDataDir = new File(savedUserDataPath);
        if (!savedDataDir.isDirectory() && !savedDataDir.mkdirs()) {
            System.out.println("Unable to create folder for saves");
            return false;
        }
        if (savedDataDir.isDirectory()) {
            File[] files = savedDataDir.listFiles();

            for (File userFile : files) {

                if (userFile.exists() && userFile.canRead()) {
                    User restoreUser;
                    try (JsonParser parser = factory.createParser(userFile)) {
                        parser.setCodec(new ObjectMapper());
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            restoreUser = parser.readValueAs(User.class);

                            IWinImpl.userMap.put(restoreUser.username(), restoreUser);
                            for (Post restorePost : restoreUser.blogUnchecked()) {
                                //don't add rewinned posts to lookup
                                if (restoreUser.username().equals(restorePost.author())) {
                                    IWinImpl.postLookup.put(restorePost.postId(), restorePost);
                                    IWinImpl.postCounter = Math.max(IWinImpl.postCounter, restorePost.postId());
                                }
                            }

                            ServerMain.logger.add("Restored user: \n" + restoreUser.username());
                        }
                    } catch (IOException | NullPointerException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }
        }

        //retrieves last time the rewards check was made, if a log exists
        File previousLog = new File(savedDataPath + File.separatorChar + "latest.json");

        if (previousLog.exists() && previousLog.canRead()) {
            try (JsonParser parser = factory.createParser(previousLog)) {
                parser.setCodec(new ObjectMapper());
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    Log latest = parser.readValueAs(Log.class);
                    ServerMain.lastCheck = latest.lastCheck;
                    latest = null;
                    System.gc();
                }
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
                return false;
            }
        }


        return true;
    }

    static final String savedUserDataPath = "saved_data" + File.separatorChar + "users";
    static final String savedDataPath = "saved_data";


    public static void saveAll() {

        JsonFactory factory = new JsonFactory();

        File savedDataDir = new File(savedUserDataPath);

        if (!savedDataDir.exists()) {

            if (!savedDataDir.mkdir()) {
                System.out.println("Error while saving user data");
                return;
            }

        }

        for (User user : IWinImpl.userMap.values()) {
            try (JsonGenerator generator = factory.createGenerator(
                    new File(savedUserDataPath + File.separatorChar + user.username() + ".json"), JsonEncoding.UTF8)
            ) {
                ServerMain.logger.add("Saving: " + user.username() + '\n');
                generator.setCodec(new ObjectMapper());
                generator.useDefaultPrettyPrinter();
                generator.writeObject(user);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        savedDataDir = new File(savedDataPath);
        if (!savedDataDir.exists()) {

            if (!savedDataDir.mkdir()) {
                System.out.println("Error while saving user data");
                return;
            }

        }

        try (JsonGenerator generator = factory.createGenerator(new File(savedDataPath + File.separatorChar + "latest.json"), JsonEncoding.UTF8)) {
            generator.setCodec(new ObjectMapper());
            generator.useDefaultPrettyPrinter();
            generator.writeObject(new Log(ServerMain.lastCheck, ServerMain.logger));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    record Log(long lastCheck, List<String> logs) {
    }

}
