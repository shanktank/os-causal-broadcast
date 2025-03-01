import java.io.Serializable;
import java.util.Arrays;

public class Clock implements Serializable {
    //private static final long serialVersionUID = 1L;

    public int nodeID = -1;
    public int[] clock = new int[Node.TOTAL_PROCESSES];

    public Clock(int nodeID) {
        this.nodeID = nodeID;
        //clock = new int[Node.TOTAL_PROCESSES];
        //clock = new int[] {0, 0, 0, 0};
    }
    public Clock(int nodeID, int[] clock) {
        this.nodeID = nodeID;
        //this.clock = clock.clone();
        this.clock = Arrays.copyOf(clock, clock.length);
        //System.arraycopy(clock, 0, this.clock, 0, Node.TOTAL_PROCESSES);
    }

    public synchronized Clock copy() {
        //return (Clock) super.clone();
        return new Clock(nodeID, copyClock());
    }

    public synchronized int[] copyClock() {
        return Arrays.copyOf(clock, clock.length);
        //return ((Clock)super.clone()).clock;
        //return new Clock(nodeID, Arrays.copyOf(clock, clock.length));
        //int[] newClock = new int[Node.TOTAL_PROCESSES];
        //System.arraycopy(clock, 0, newClock, 0, Node.TOTAL_PROCESSES);
        //return newClock;
    }

    public synchronized void increment(int index) {
        clock[index]++;
    }
    public synchronized void merge(int[] messageClock) {
    //public void merge(Clock messageClock) {
        for (int i = 0; i < Node.TOTAL_PROCESSES; i++) {
            clock[i] = Math.max(clock[i], messageClock[i]);
        }
        System.out.println("\tClock merged to " + toString());
    }

    public synchronized boolean isDeliverable(Message message) {
        //int[] mvc = Arrays.copyOf(message.vectorClock, message.vectorClock.length);
		// TODO: hold on did this stand for myvectorclock or message vector clock
        int[] mvc = message.copyClock(); // TODO: Also copy our own clock for comparisons?
		int sID = message.senderID;
		int[] vc = copyClock();

        boolean deliverable = true;
        String notDeliverableComps = "";

        //System.out.print("\t       "); for (int i = 0; i < nodeID; i++) { System.out.print("  "); System.out.flush(); } System.out.println("▼");
        System.out.println("\tnode: " + toString() + " (" + nodeID + ")");
        //System.out.print("\t       "); System.out.flush();
        notDeliverableComps += "\t       ";
        for (int i = 0; i < Node.TOTAL_PROCESSES; i++) {
            // TODO: check i vs nodeID, senderID vs nodeID, or i vs senderID?
            //if (i != nodeID) {
            //if (sID != nodeID) { // surely not this one
            if (i != sID) {
                //if (mvc[i] > vc[i]) {
                if (vc[i] < mvc[i]) {
                    deliverable = false;
                    //System.out.print("× ");
                    notDeliverableComps += "x ";
                } else {
                    //System.out.print("  ");
                    notDeliverableComps += "  ";
                }
            } else {
                //if (mvc[i] != vc[i] - 1) {
                //if (vc[i] - 1 < mvc[i]) {
                if (vc[i] + 1 != mvc[i]) {
                //if (vc[i] + 1 < mvc[i]) {
                    deliverable = false;
                    //System.out.print("X ");
                    notDeliverableComps += "X ";
                } else {
                    //System.out.print("  ");
                    notDeliverableComps += "  ";
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
        return "[" + vc[0] + "," + vc[1] + "," + vc[2] + "," + vc[3] + "]";
    }
}