package server.data;

/**
 * Utility class to check if text elements are valid. Only checks for text length and numeric values since no special case need to addressed in this implementation
 */
public class TextValidator {

    public static boolean validateUsername(String username) {
        return username.length() >= 1 && username.length() <= 20;
    }

    public static boolean validatePassword(String password) {
        return password.length() >= 1 && password.length() <= 20;
    }

    public static boolean validatePostTitle(String title) {
        return title.length() >= 1 && title.length() <= 20;
    }

    /**
     * @param content content of the post to check
     * @return valid if text contains at least one letter but less than 500
     */
    public static boolean validatePostContent(String content) {
        return content.length() >= 1 && content.length() <= 500;
    }

    /**
     * @param content content of the comment to check
     * @return valid if text contains at least one letter but less than 100
     */
    public static boolean validateComment(String content) {
        return content.length() >= 1 && content.length() <= 100;
    }

    public static boolean isNumeric(String str) {return str != null && str.matches("^-?[\\d.]+");}


}