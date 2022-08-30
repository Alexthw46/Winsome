package server.data;

/**
 * @param author author of the comment author
 * @param content body of the comment
 * @param timestamp when the comment was made, in milliseconds
 */
public record Comment(String author, String content, long timestamp) {

    public String format(){
        return "* " + author() + " : " + content() + '\n';
    }

}
