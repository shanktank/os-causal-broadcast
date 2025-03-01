import java.io.Serializable;
import java.util.Arrays;

public class Clock implements Serializable {
    //private static final long serialVersionUID = 1L;

    public int nodeID = -1;
    public int[] clock = new int[Node.TOTAL_PROCESSES];

    public Clock(int nodeID) {
        this.nodeID = nodeID;
    }
	
    public Clock(int nodeID, int[] clock) {
        this.nodeID = nodeID;
        this.clock = Arrays.copyOf(clock, clock.length);
    }

    public synchronized Clock copy() {
        return new Clock(nodeID, copyClock());
    }

    public synchronized int[] copyClock() {
        return Arrays.copyOf(clock, clock.length);
    }

    public synchronized void increment(int index) {
        clock[index]++;
    }
	
    public synchronized void merge(int[] messageClock) {
        for (int i = 0; i < Node.TOTAL_PROCESSES; i++) {
            clock[i] = Math.max(clock[i], messageClock[i]);
        }
        System.out.println("\tClock merged to " + toString());
    }

    public synchronized boolean isDeliverable(Message message) {
        int[] mvc = message.copyClock(); // TODO: Also copy our own clock for comparisons?
		int sID = message.senderID;
		int[] vc = copyClock();

        boolean deliverable = true;
        String notDeliverableComps = "";
        //System.out.print("\t       "); for (int i = 0; i < nodeID; i++) { System.out.print("  "); System.out.flush(); } System.out.println("▼");
        System.out.println("\tnode: " + toString() + " (" + nodeID + ")");
        notDeliverableComps += "\t       ";
        for (int i = 0; i < Node.TOTAL_PROCESSES; i++) {
            // TODO: check i vs nodeID, senderID vs nodeID, or i vs senderID?
            //if (i != nodeID) {
            //if (sID != nodeID) { // surely not this one
            if (i != sID) {
                //if (mvc[i] > vc[i]) {
                if (vc[i] < mvc[i]) {
                    deliverable = false;
                    notDeliverableComps += " x  ";
                } else {
                    notDeliverableComps += "    ";
                }
            } else {
                //if (mvc[i] != vc[i] - 1) {
                //if (vc[i] - 1 < mvc[i]) {
                if (vc[i] + 1 != mvc[i]) {
                //if (vc[i] + 1 < mvc[i]) {
                    deliverable = false;
                    notDeliverableComps += " X  ";
                } else {
                    notDeliverableComps += "    ";
                }
            }
        }
        if (!deliverable) System.out.println(notDeliverableComps);
        System.out.println("\tmesg: " + message.toString() + " (" + message.senderID + ")");
        //System.out.print("\t       "); for (int i = 0; i < nodeID; i++) { System.out.print("  "); System.out.flush(); } System.out.println("▲");

        return deliverable;
    }

    public synchronized String toString() {
        int[] vc = copyClock();
		return String.format("[%03d,%03d,%03d,%03d]", vc[0], vc[1], vc[2], vc[3]);
    }
}