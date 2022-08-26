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
    static final ServerConfig defaults = new ServerConfig("localhost", "239.255.32.32", 1080, 44444, "AUTHENTICATOR", 10, 100L, 0.75F);

    public static boolean initialize() {

        ObjectMapper om = new ObjectMapper(new YAMLFactory());

        File configs = new File("config" + File.separatorChar + "serverConfigs.yaml");

        //load configs from file or create one with the default values
        try {
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


        JsonFactory factory = new JsonFactory();

        //restores the users and posts from local files

        File savedDataDir = new File(savedUserDataPath);

        if (savedDataDir.exists() || savedDataDir.isDirectory()) {

            File[] files = savedDataDir.listFiles();

            assert files != null;
            for (File userFile : files) {

                if (userFile.exists() && userFile.canRead()) {
                    User restoreUser;
                    try (JsonParser parser = factory.createParser(userFile)) {
                        parser.setCodec(new ObjectMapper());
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            restoreUser = parser.readValueAs(User.class);

                            ServerMain.userMap.put(restoreUser.userId(), restoreUser);
                            ServerMain.userIdLookup.put(restoreUser.username(), restoreUser.userId());
                            for (Post restorePost : restoreUser.blogUnchecked()) {
                                //ignore rewinned posts
                                if (restoreUser.username().equals(restorePost.username())) {
                                    ServerMain.postLookup.put(restorePost.postId(), restorePost);
                                    IWinImpl.postCounter = Math.max(IWinImpl.postCounter, restorePost.postId());
                                }
                            }
                            System.out.println("Deserialized object from JSON");
                            System.out.println("-----------------------");
                            System.out.println(restoreUser);
                            System.out.println("-----------------------");
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

        for (User user : ServerMain.userMap.values()) {
            try (JsonGenerator generator = factory.createGenerator(
                    new File(savedUserDataPath + File.separatorChar + user.username() + ".json"), JsonEncoding.UTF8)
            ) {

                generator.setCodec(new ObjectMapper());
                generator.useDefaultPrettyPrinter();
                generator.writeObject(user);

            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Saving: " + user.userId() + " | " + user.username() + '\n');
        }

        savedDataDir = new File(savedDataPath);
        if (!savedDataDir.exists()) {

            if (!savedDataDir.mkdir()) {
                System.out.println("Error while saving user data");
                return;
            }

        }

        try (JsonGenerator generator = factory.createGenerator(
                new File(savedDataPath + File.separatorChar + "latest.json"), JsonEncoding.UTF8)
        ) {
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
