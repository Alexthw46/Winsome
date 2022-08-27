package client;


import common.Triplet;

import java.util.Scanner;
import java.util.UUID;

public class CommandParser {

    public static void printHelp(){
        System.out.println("""
                List of available commands:\s
                login <username> <password>\s
                register <username> <password> <tags> | Up to five tags can be inserted, at least one is needed\s
                shutdown - close both client and server\s
                logout - logs out from account and close the client\s
                blog\s
                list user \s
                list followers \s
                list following\s
                show feed\s
                show post <postId> \s
                post <"Title"> <"Content"> | Title and Content needs to be enclosed between " " \s
                delete <postId> | Only usable by the author of the post \s
                comment <postId> <"Comment"> | Comment needs to be enclosed between " " \s
                rewin <postId> \s
                rate <postId> <rating> | rating can be +1 or -1 \s
                follow <username> \s
                unfollow <username> \s
                wallet \s
                """);
    }


    /**
     * @param scanner to get and parse the string
     * @param userToken token that will be encapsulated in the record
     * @return Triplet < userToken, input without operations , operation code if match found, -2 if no match, -3 if not enough arguments >
     */
    public static Triplet getAndParseCommand(Scanner scanner, UUID userToken) {
        String input = scanner.nextLine();

        String[] tokens = input.split(" ");


        int opCode = switch (tokens[0]) {
            case "help" -> -1;
            case "logout", "shutdown" -> 0;
            case "login" -> tokens.length > 2 ? 100 : NOT_ENOUGH_ARGS_CODE;
            case "register" -> tokens.length > 3 ? -100 : NOT_ENOUGH_ARGS_CODE;
            case "list" ->
                    tokens.length > 1 ? switch (tokens[1]) {
                        case "user" -> 1;
                        case "followers" -> 2;
                        case "following" -> 3;
                        default -> UNKNOWN_OP_CODE;
                    } : NOT_ENOUGH_ARGS_CODE;
            case "follow" -> tokens.length > 1 ? 4 : NOT_ENOUGH_ARGS_CODE;
            case "unfollow" -> tokens.length > 1 ? 5 : NOT_ENOUGH_ARGS_CODE;
            case "blog" -> 6;
            case "post" -> tokens.length > 2 ? 10 : NOT_ENOUGH_ARGS_CODE;
            case "delete" -> tokens.length > 1 ? 11 : NOT_ENOUGH_ARGS_CODE;
            case "rewin" -> tokens.length > 1 ? 12 : NOT_ENOUGH_ARGS_CODE;
            case "comment" -> tokens.length > 2 ? 13 : NOT_ENOUGH_ARGS_CODE;
            case "rate" -> tokens.length > 2 ? 14 : NOT_ENOUGH_ARGS_CODE;
            case "show" -> tokens.length > 1 ?
                    switch (tokens[1]) {
                        case "feed" -> 20;
                        case "post" -> tokens.length > 2 ? 21 : NOT_ENOUGH_ARGS_CODE;
                        default -> UNKNOWN_OP_CODE;
                    } : NOT_ENOUGH_ARGS_CODE;
            case "wallet" -> (tokens.length > 1 && tokens[1].equals("btc")) ? 31 : 30;
            default -> UNKNOWN_OP_CODE;
        };

        String format = switch (opCode){
            case 0 -> tokens[0];
            case NOT_ENOUGH_ARGS_CODE, UNKNOWN_OP_CODE -> input;
            case 21 -> input.substring(1 + tokens[0].length() + tokens[1].length()).trim();
            default -> input.substring(tokens[0].length()).trim();
        };

        return new Triplet(format, opCode, userToken);
    }

    public static final int NOT_ENOUGH_ARGS_CODE = -3;
    public static final int UNKNOWN_OP_CODE = -2;

}
