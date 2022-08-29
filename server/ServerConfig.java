package server;

public record ServerConfig(
        String ServerAddress,
        String MulticastAddress,
        Integer TCPPort,
        Integer UDPPort,
        String RegHost,
        Integer RegPort,
        Float AuthorReward,
        Long PointsAwardInterval,
        Long selectTimeout
        ){

}
