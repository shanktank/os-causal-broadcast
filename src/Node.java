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
    private static final String[] ADDRESSES = { "dc01", "dc02", "dc03", "dc05" };
    private static final int PORT = 9000;

    public static final int TOTAL_PROCESSES = 4;
    private static final ExecutorService executor = Executors.newFixedThreadPool(TOTAL_PROCESSES);

    private static int nodeID;
    public static Clock nodeVectorClock;

	private static final List<Message> waiting = Collections.synchronizedList(new ArrayList<>()); // Messages held back from delivery
	private static final List<Integer> orderDelivered = Collections.synchronizedList(new ArrayList<>());

    private static final Object mutex = new Object(); // Mutex for thread critical sections
    private static final Random random = new Random();

    private static final int MESSAGES_TO_SEND = 100;
    private static final int MESSAGES_TO_RECEIVE_TOTAL = MESSAGES_TO_SEND * (TOTAL_PROCESSES - 1);
    private static final int MESSAGES_TO_RECEIVE_PER_THREAD = MESSAGES_TO_SEND;
    private static int messagesSent = 0;
    private static int messagesReceived = 0;
    private static int messagesDelivered = 0;
    private static int biggestQueue = 0;
    private static int totalHeldBack = 0;

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
            // Connect out to all processes with otherID < nodeID
            for (int otherID = 0; otherID < nodeID; otherID++) {
                Socket s = connectToProcess(otherID);
                sockets.add(s);
                System.out.println("C" + nodeID + ": connected to C" + otherID);
            }
			
            // Accept connections from processes with otherID > nodeID
            int connectionsToAccept = TOTAL_PROCESSES - 1 - nodeID;
            for (int i = 0; i < connectionsToAccept; i++) {
                Socket s = serverSocket.accept();
                sockets.add(s);
                System.out.println("C" + nodeID + ": accepted connection.");
            }
			
			// Everyone's connected, now the actual work can begin
            System.out.println("C" + nodeID + ": all connections established.\n");
			
			// Object streams to handle our messages
            for (Socket socket : sockets) {
                outputStreams.add(new ObjectOutputStream(socket.getOutputStream()));
                inputStreams.add(new ObjectInputStream(socket.getInputStream()));
            }
			
			// Spawn thread a for each socket to listen and one thread to broadcast to all listeners
            for (ObjectInputStream inputStream : inputStreams)
				executor.submit(() -> listenForMessages(inputStream));
            executor.submit(() -> broadcastMessages(outputStreams));

			// Wait until all threads have completed all their work then close sockets
            while (messagesSent < MESSAGES_TO_SEND || messagesReceived < MESSAGES_TO_RECEIVE_TOTAL)
                try { Thread.sleep(100); } catch(InterruptedException e) {}
            for (Socket s : sockets) s.close();

			// Report results
			System.out.println();
            System.out.println("Messages sent: " + messagesSent);
            System.out.println("Messages received: " + messagesReceived);
            System.out.println("Messages delivered: " + messagesDelivered);
            System.out.println("Biggest queue size: " + biggestQueue);
            System.out.println("Total held back: " + totalHeldBack + "/" + MESSAGES_TO_RECEIVE_TOTAL);
            System.out.println("Order delivered: " + orderDelivered);
        } catch (IOException e) {
            System.out.println("Exception in main: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private static void listenForMessages(ObjectInputStream inputStream) {
        try {
			// Each thread will receive exactly 100 messages, after which it is finished and need no longer listen for more
			for (int mr = 0; mr < MESSAGES_TO_RECEIVE_PER_THREAD; mr++) {
                Message message = (Message) inputStream.readObject();
				
				Thread.sleep(random.nextInt(4) + 1); // Delay 1-5ms
				
                synchronized(mutex) { // Use mutex so only one thread can process a message at a time
                    System.out.println("From C" + message.getSenderID() + ": M" + message.getMessageNumber() + " " + message.toString());
                    if(nodeVectorClock.isDeliverable(message)) {
						deliverMessage(message);
                        System.out.println("\tC" + nodeID + ": CLOCK UPDATED TO " + nodeVectorClock.toString());
						
						// If the message can be delivered, check the delayed list for other deliverable messages
                        boolean check;
                        do {
                            check = false;
                            for(int i = 0; i < waiting.size(); i++) {
                                Message m = waiting.get(i);
                                System.out.println("\tChecking undelivered: M" + m.getMessageNumber() + " from C" + m.getSenderID());
                                if(nodeVectorClock.isDeliverable(m)) {
									deliverMessage(m);
									System.out.println("\t\tMessage is now deliverable: M" + m.getMessageNumber() + " from C" + m.getSenderID());
                                    System.out.println("\t\tC" + nodeID + ": CLOCK UPDATED TO " + nodeVectorClock.toString());
                                    waiting.remove(i);
									System.out.println("\t\tWaiting queue size: " + waiting.size());
                                    check = true;
                                    break;
                                }
                            }
                        } while(check);
                    } else {
						// If a message isn't causally ready, add it to the delayed queue
						delayDelivery(message);
                        System.out.println("\tDelivery delayed. Waiting queue size: " + waiting.size());
                    }
					
                    messagesReceived++;
                }
            }            
        } catch (IOException | InterruptedException | ClassNotFoundException e) {
            System.out.println("Exception in listenForMessages: " + e.getMessage());
        }
    }

    private static void broadcastMessages(List<ObjectOutputStream> outputStreams) {
		while (messagesSent < MESSAGES_TO_SEND) {
            try { Thread.sleep(random.nextInt(10)); } catch (InterruptedException e) {} // Sleep 0-9ms then broadcast
			Clock copy = nodeVectorClock.increment(nodeID); // Use copy below for thread safety
			Message message = new Message(nodeID, messagesSent + 1, copy);
			System.out.println("C" + nodeID + ": M" + (messagesSent + 1) + " PREPARED. CLOCK UPDATED TO " + copy.toString() + ". BROADCASTING...");
			for (ObjectOutputStream outputStream : outputStreams) { // Send a message to all other machines
				try {
					outputStream.writeObject(message);
					outputStream.flush();
				} catch (IOException e) {
					System.out.println("Exception in broadcastMessage: " + e.getMessage());
				}
			}
			messagesSent++;
        }
    }

	private static synchronized void deliverMessage(Message message) {
		messagesDelivered++;
		orderDelivered.add(message.getMessageNumber());
		nodeVectorClock.increment(message.getSenderID());
	}
	
	// When a message is not causally ready, queue it for later delivery
	private static synchronized void delayDelivery(Message message) {
		waiting.add(message);
		totalHeldBack++;
		if (waiting.size() > biggestQueue) biggestQueue = waiting.size();
	}

    // Attempts to establish a connection, retrying if it is not ready yet
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
