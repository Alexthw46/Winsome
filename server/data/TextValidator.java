package server.data;

public class TextValidator {

    public static boolean validateUsername(String username) {
        if (username.length() < 1 || username.length() > 20) return false;
        if (username.contains(" ")) return false;
        return true;
    }

    public static boolean validatePassword(String password) {
        if (password.length() < 1 || password.length() > 20) {
            return false;
        }
        return true;
    }

    public static boolean validatePostTitle(String title) {
        if (title.length() < 1 || title.length() > 200) return false;
        return true;
    }

    public static boolean validatePostContent(String content) {
        if (content.length() < 1 || content.length() > 200) return false;

        return true;
    }

    public static boolean isNumeric(String str) {return str != null && str.matches("[\\d.]+");}


}