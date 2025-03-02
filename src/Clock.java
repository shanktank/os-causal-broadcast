import java.io.Serializable;
import java.util.Arrays;

public class Clock implements Serializable {
    public int nodeID = -1;
    public int[] clock = new int[Node.TOTAL_PROCESSES];
	
	private static final Object mutex = new Object();

    public Clock(int nodeID) {
        this.nodeID = nodeID;
    }

    public Clock(int nodeID, int[] clock) {
        this.nodeID = nodeID;
        //this.clock = Arrays.copyOf(clock, clock.length);
		this.clock = clock;
    }

    public synchronized Clock copy() {
        return new Clock(nodeID, copyClock());
    }

    public int[] copyClock() {
        synchronized (mutex) { return Arrays.copyOf(clock, clock.length); }
    }

    public synchronized void increment(int index) {
        synchronized (mutex) { clock[index]++; }
    }

    public boolean isDeliverable(Message message) {
        int[] mvc = message.copyClock();
        int sID = message.senderID;
        int[] vc = copyClock();

        boolean deliverable = true;
        String notDeliverableComps = "";
        System.out.println("\tnode: " + toString() + " (" + nodeID + ")");
        notDeliverableComps += "\t       ";
        for (int i = 0; i < Node.TOTAL_PROCESSES; i++) {
            if (i != sID) {
                if (vc[i] < mvc[i]) {
                    deliverable = false;
                    notDeliverableComps += " x  ";
                } else {
                    notDeliverableComps += "    ";
                }
            } else {
                if (vc[i] + 1 != mvc[i]) {
                    deliverable = false;
                    notDeliverableComps += " X  ";
                } else {
                    notDeliverableComps += "    ";
                }
            }
        }
        if (!deliverable) System.out.println(notDeliverableComps);
        System.out.println("\tmesg: " + message.toString() + " (" + message.senderID + ")");

        return deliverable;
    }

    public String toString() {
        int[] vc = copyClock();
        return String.format("[%03d,%03d,%03d,%03d]", vc[0], vc[1], vc[2], vc[3]);
    }
}