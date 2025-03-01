import java.io.Serializable;
import java.util.Arrays;

public class Message implements Serializable {
    //private static final long serialVersionUID = 1L;

    public int senderID;
    public int messageNumber;
    public Clock vectorClock;

    public Message(int senderID, int messageNumber, Clock vectorClock) {
        this.messageNumber = messageNumber;
        this.senderID = senderID;
        //this.vectorClock = vectorClock;
		this.vectorClock = vectorClock.copy();
    }

	public synchronized int[] copyClock() {
		return vectorClock.copyClock();
	}

    public synchronized String toString() {
        return vectorClock.toString();
    }
}