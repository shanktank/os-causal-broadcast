import java.io.Serializable;
import java.util.Arrays;

public class Message implements Serializable {
    //private static final long serialVersionUID = 1L;

    public int senderID;
    public int messageNumber;
    public Clock vectorClock;

    public Message(int senderID, int messageNumber, Clock vectorClock) {
        this.senderID = senderID;
		this.messageNumber = messageNumber;
        this.vectorClock = vectorClock.copy();
    }

    public int[] copyClock() {
        return vectorClock.copyClock();
    }

    public String toString() {
        return vectorClock.toString();
    }
}