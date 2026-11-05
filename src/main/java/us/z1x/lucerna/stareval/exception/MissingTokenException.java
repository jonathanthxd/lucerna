package us.z1x.lucerna.stareval.exception;

public class MissingTokenException extends ParseException {
    public MissingTokenException(String message, int index) {
        super(message + " at index " + index);
    }
}
