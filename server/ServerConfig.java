package server;

public record ServerConfig(
        String ServerAddress,
        String MulticastAddress,
        Integer TCPPort,
        Integer UDPPort,
        String RegHost,
        Integer RegPort,

        Long PointsAwardInterval,
        Float AuthorReward
        ){}
