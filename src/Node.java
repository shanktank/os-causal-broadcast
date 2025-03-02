import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;

public class Node {
	//public static final String[] ADDRESSES = { "localhost", "localhost", "localhost", "localhost" };
    private static final String[] ADDRESSES = { "dc01", "dc02", "dc03", "dc04" };
    private static final int PORT = 9000;

    public static final int TOTAL_PROCESSES = 4;
    private static final ExecutorService executor = Executors.newFixedThreadPool(TOTAL_PROCESSES);
    private static final List<Integer> listeners = new ArrayList<>();

    private static int nodeID;
    public static Clock nodeVectorClock;

	private static final List<Message> waiting = Collections.synchronizedList(new ArrayList<>()); // Messages held back from delivery
	private static final List<Integer> orderDelivered = Collections.synchronizedList(new ArrayList<>());

    private static final Object mutex = new Object();
    private static final Random random = new Random();

    private static final int MESSAGES_TO_SEND = 100;
    private static final int MESSAGES_TO_RECEIVE_TOTAL = MESSAGES_TO_SEND * (TOTAL_PROCESSES - 1);
    private static final int MESSAGES_TO_RECEIVE_PER_THREAD = MESSAGES_TO_SEND;
    private static int messagesSent = 0;
    private static int messagesReceived = 0;
    private static int messagesDelivered = 0;
    private static int biggestQueue = 0;
    private static int totalHeldBack = 0;

    private static final List<Long> timesBegin = Collections.synchronizedList(new ArrayList<>());
    private static final List<Long> timesEnd = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        if (args.length != 1 || Integer.parseInt(args[0]) < 0 || Integer.parseInt(args[0]) >= TOTAL_PROCESSES) {
            System.err.println("Usage: java Node <0|1|2|3>");
            System.exit(1);
        }
		
		nodeID = Integer.parseInt(args[0]);
        nodeVectorClock = new Clock(nodeID);
		
        List<Socket> sockets = new ArrayList<>(); // A list to hold all sockets to other processes
        List<ObjectOutputStream> outputStreams = new ArrayList<>(); // Each socket's output stream for message sending
        List<ObjectInputStream> inputStreams = new ArrayList<>(); // Each socket's input stream for message listening

        System.out.println("C" + nodeID + ": starting...");
        try (ServerSocket serverSocket = new ServerSocket(PORT + nodeID)) {
            // Connect out to all processes with ID < nodeID
            for (int otherID = 0; otherID < nodeID; otherID++) {
                Socket s = connectToProcess(otherID);
                sockets.add(s);
                listeners.add(otherID);
                System.out.println("C" + nodeID + ": connected to C" + otherID);
            }

            // Accept connections from processes with ID > nodeID
            int connectionsToAccept = TOTAL_PROCESSES - 1 - nodeID;
            for (int i = 0; i < connectionsToAccept; i++) {
                Socket s = serverSocket.accept();
                sockets.add(s);
                listeners.add(i + nodeID + 1);
                System.out.println("C" + nodeID + ": accepted connection.");
            }

            // All socket connections should now be established
            if (sockets.size() == 3) {
                System.out.println("C" + nodeID + ": all 3 connections established.\n");
            } else {
				System.out.println("C" + nodeID + ": something weird happened.\n");
			}
			
			long start = Instant.now().toEpochMilli();

            // Start a thread to listen for messages from each connection
            for (Socket socket : sockets) {
                outputStreams.add(new ObjectOutputStream(socket.getOutputStream()));
                inputStreams.add(new ObjectInputStream(socket.getInputStream()));
            }

            for (ObjectInputStream inputStream : inputStreams)
				executor.submit(() -> listenForMessages(inputStream));
            executor.submit(() -> broadcastMessages(outputStreams));

            while (messagesSent < MESSAGES_TO_SEND || messagesReceived < MESSAGES_TO_RECEIVE_TOTAL)
                try { Thread.sleep(100); } catch(InterruptedException e) {}

            for (Socket s : sockets) s.close();

            System.out.println("\nMessages sent: " + messagesSent);
            System.out.println("Messages received: " + messagesReceived);
            System.out.println("Messages delivered: " + messagesDelivered);
            System.out.println("Biggest queue size: " + biggestQueue);
            System.out.println("Total held back: " + totalHeldBack + "/" + MESSAGES_TO_RECEIVE_TOTAL);
            //System.out.println("Order delivered: " + orderDelivered);
            //System.out.println("timesBegin: " + timesBegin);
            //System.out.println("timesEnd: " + timesEnd);
            System.out.println("timesBegin.size: " + timesBegin.size());
            System.out.println("timesEnd.size: " + timesBegin.size());
            boolean fuck = false;
            for (int i = 0; i < timesBegin.size() - 1; i++) if(timesBegin.get(i) > timesBegin.get(i+1)) { System.out.println("timeBegin out of order"); fuck = true; }
            if (!fuck) System.out.println("timeBegin is in increasing order");
            fuck = false;
            for (int i = 0; i < timesEnd.size() - 1; i++) if(timesEnd.get(i) > timesEnd.get(i+1)) { System.out.println("timeEnd out of order"); fuck = true; }
            if (!fuck) System.out.println("timeEnd is in increasing order");
            fuck = false;
            for (int i = 0; i < timesEnd.size(); i++) if(timesBegin.get(i) > timesEnd.get(i)) { System.out.println("times out of order"); fuck = true; }
            if (!fuck) System.out.println("times are in increasing order");
			System.out.println((Instant.now().toEpochMilli() - start) + "ms");
        } catch (EOFException e) {
            // Closed normally
        } catch (IOException e) {
            System.out.println("Exception in main: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private static void listenForMessages(ObjectInputStream inputStream) {
        try {
			for (int mr = 0; mr < MESSAGES_TO_RECEIVE_PER_THREAD; mr++) {
                Message message = (Message) inputStream.readObject();
                //if (message == null) break; // TODO: send null message to indicate session termination from one process to the others?
                try { Thread.sleep(random.nextInt(4) + 1); } catch (InterruptedException e) {e.printStackTrace();}
                synchronized(mutex) {
                    timesBegin.add(Instant.now().toEpochMilli());
                    System.out.println("From C" + message.senderID + ": M" + message.messageNumber + " " + message.toString());
                    if(nodeVectorClock.isDeliverable(message)) {
                        messagesDelivered++;
                        orderDelivered.add(message.messageNumber);
						nodeVectorClock.increment(message.senderID);
                        System.out.println("\tC" + nodeID + ": CLOCK UPDATED TO " + nodeVectorClock.toString());
                        boolean check;
                        do {
                            check = false;
                            for(int i = 0; i < waiting.size(); i++) {
                                Message m = waiting.get(i);
                                System.out.println("\tChecking undelivered: M" + m.messageNumber + " from C" + m.senderID);
                                if(nodeVectorClock.isDeliverable(m)) {
                                    messagesDelivered++;
                                    orderDelivered.add(m.messageNumber);
                                    System.out.println("\t\tMessage is now deliverable: M" + m.messageNumber + " from C" + m.senderID);
									nodeVectorClock.increment(m.senderID);
                                    System.out.println("\t\tC" + nodeID + ": CLOCK UPDATED TO " + nodeVectorClock.toString());
                                    waiting.remove(i);
                                    check = true;
                                    break;
                                }
                            }
                        } while(check);
                    } else {
                        System.out.println("\tDELAYING DELIVERY");
                        waiting.add(message);
                        totalHeldBack++;
                        if (waiting.size() > biggestQueue) biggestQueue = waiting.size();
                        System.out.println("\tWaiting queue size: " + waiting.size());
                    }
                    messagesReceived++;
                    timesEnd.add(Instant.now().toEpochMilli());
                }
            }            
        } catch (EOFException e) {
            // Closed normally
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Exception in listenForMessages: " + e.getMessage());
        }
    }

    private static void broadcastMessages(List<ObjectOutputStream> outputStreams) {
        while (messagesSent < MESSAGES_TO_SEND) {
            try { Thread.sleep(random.nextInt(10)); } catch (InterruptedException e) {} // Sleep 0-9ms then broadcast
            synchronized (mutex) {
                // Send a message to all other machines
                nodeVectorClock.increment(nodeID); // Increment my own clock
                System.out.println("C" + nodeID + ": M" + (messagesSent + 1) + " PREPARED, CLOCK UPDATED TO " + nodeVectorClock.toString());
                broadcastMessage(outputStreams, new Message(nodeID, messagesSent + 1, nodeVectorClock));
                messagesSent++;
            }
        }
    }

    private static void broadcastMessage(List<ObjectOutputStream> outputStreams, Message message) {
        int i = 0;
        for (ObjectOutputStream outputStream : outputStreams) {
            System.out.println("\tC" + nodeID + ": sending M" + message.senderID + " " + message.toString() + " to C" + listeners.get(i)); i++;
            try {
                outputStream.writeObject(message);
                outputStream.flush();
            } catch (EOFException e) {
                // Closed normally
            } catch (IOException e) {
                System.out.println("Exception in broadcastMessage: " + e.getMessage());
            }
        }
    }

    // Attempts to establish a connection, retrying if it is not ready yet.
    private static Socket connectToProcess(int otherID) throws IOException {
        final int MAX_ATTEMPTS = 6000;
        int attempts = 0;
        while (true) {
            try {
                return new Socket(ADDRESSES[otherID], PORT + otherID);
            } catch (IOException e) {
                attempts++;
                if (attempts >= MAX_ATTEMPTS) {
                    System.err.println("Failed to connect to C" + otherID + ".");
                    throw e;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying connection");
                }
            }
        }
    }
}