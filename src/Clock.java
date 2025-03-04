import java.io.Serializable;
import java.util.Arrays;

public class Clock implements Serializable {
    private int nodeID = -1;
    private int[] clock = new int[Node.TOTAL_PROCESSES];
	
	private static final Object mutex = new Object();

    public Clock(int nodeID) {
        this.nodeID = nodeID;
    }

    public Clock(int nodeID, int[] clock) {
        this.nodeID = nodeID;
		this.clock = clock;
    }

	// Take copy of object for thread-safe use
    public synchronized Clock copy() {
        return new Clock(nodeID, copyClock());
    }

	// Take copy of clock for thread-safe use
    public int[] copyClock() {
        synchronized (mutex) {
			return Arrays.copyOf(clock, clock.length);
		}
    }

	// Increment the clock at the index of the sender when it's delivered
    public synchronized Clock increment(int index) {
        synchronized (mutex) {
			clock[index]++;
			return copy();
		}
    }

	// Check if a message satisfies the conditions:
	//  Message clock == node clock + 1 at index of sender
	//  Message clock < node clock for all other indices
	// Also display and compare the clocks visually for confirmation
    public boolean isDeliverable(Message message) {
        int[] mvc = message.copyClock();

        boolean deliverable = true;
        String notDeliverableComps = "";
        System.out.println("\tnode: " + toString() + " (C" + nodeID + ")");
        notDeliverableComps += "\t       ";
        for (int i = 0; i < Node.TOTAL_PROCESSES; i++) {
            if (i != message.getSenderID()) {
                if (clock[i] < mvc[i]) {
                    deliverable = false;
                    notDeliverableComps += " x  ";
                } else {
                    notDeliverableComps += "    ";
                }
            } else {
                if (clock[i] + 1 != mvc[i]) {
                    deliverable = false;
                    notDeliverableComps += " X  ";
                } else {
                    notDeliverableComps += "    ";
                }
            }
        }
        if (!deliverable) System.out.println(notDeliverableComps);
        System.out.println("\tmesg: " + message.toString() + " (C" + message.getSenderID() + ")");

        return deliverable;
    }

    public String toString() {
        int[] vc = copyClock();
        return String.format("[%03d,%03d,%03d,%03d]", vc[0], vc[1], vc[2], vc[3]);
    }
}