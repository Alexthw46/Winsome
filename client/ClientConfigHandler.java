package client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

public class ClientConfigHandler {

    public record ClientConfig(String ServerAddress, Integer TCPPort, String RegHost, Integer RegPort){}

     static final ClientConfig defaults = new ClientConfig("localhost", 1080, "AUTHENTICATOR", 1846);

    public static ClientConfig loadConfigs() {
        ObjectMapper om = new ObjectMapper(new YAMLFactory());

        File configs = new File("config"+ File.separatorChar +"clientConfigs.yaml");

        try {
            if (configs.createNewFile()) {
                om.writeValue(configs, defaults);
            } else {
                return om.readValue(configs, ClientConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return defaults;
    }


}
