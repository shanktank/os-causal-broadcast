import java.io.Serializable;
import java.util.Arrays;

public class Message implements Serializable {
    private int senderID;
    private int messageNumber;
    private Clock vectorClock;

    public Message(int senderID, int messageNumber, Clock vectorClock) {
        this.senderID = senderID;
		this.messageNumber = messageNumber;
        this.vectorClock = vectorClock.copy();
    }

	// Take copy of clock for thread-safe use
    public int[] copyClock() {
        return vectorClock.copyClock();
    }
	
	public int getSenderID() {
		return senderID;
	}
	
	public int getMessageNumber() {
		return messageNumber;
	}

    public String toString() {
        return vectorClock.toString();
    }
}