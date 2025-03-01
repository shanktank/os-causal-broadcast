import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;

public class Node {
    private static final String[] ADDRESSES = { "dc01", "dc02", "dc03", "dc04" };
    //public static final String[] ADDRESSES = { "localhost", "localhost", "localhost", "localhost" };
    private static final int PORT = 9000;

    public static final int TOTAL_PROCESSES = 4;
    private static final ExecutorService executor = Executors.newFixedThreadPool(TOTAL_PROCESSES);
    private static List<Integer> listeners = new ArrayList<>();

    private static int nodeID;
    public static Clock nodeVectorClock = new Clock(-1);
    private static List<Message> waiting = new ArrayList<>(); // Messages held back from delivery

    private static final Object mutex = new Object();
    private static final Random random = new Random();
	
	private static final int MESSAGES_TO_SEND = 100;
	private static final int MESSAGES_TO_RECEIVE = MESSAGES_TO_SEND * (TOTAL_PROCESSES - 1);
	private static int messagesSent = 0;
	private static int messagesReceived = 0;
	private static int biggestQueue = 0;
	private static int totalHeldBack = 0;

    public static void main(String[] args) {
        if (args.length != 1 || Integer.parseInt(args[0]) < 0 || Integer.parseInt(args[0]) >= TOTAL_PROCESSES) {
            System.err.println("Usage: java Node <0|1|2|3>");
            System.exit(1);
        }

        nodeID = Integer.parseInt(args[0]);
        //nodeVectorClock = new Clock(nodeID);
        nodeVectorClock.nodeID = nodeID;
        List<Socket> sockets = new ArrayList<>(); // A list to hold all sockets to other processes
        List<ObjectOutputStream> outputStreams = new ArrayList<>(); // A list to hold output streams for sending messages

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
            int connectionsToAccept = (TOTAL_PROCESSES - 1) - nodeID;
            for (int i = 0; i < connectionsToAccept; i++) {
                Socket s = serverSocket.accept();
                sockets.add(s);
                listeners.add(i + nodeID + 1);
                System.out.println("C" + nodeID + ": accepted connection.");
            }

            // All socket connections should now be established
            if (sockets.size() == 3) {
                System.out.println("C" + nodeID + ": all 3 connections established.\n");
            }

            // Start a thread to listen for messages from each connection
            for (Socket socket : sockets) {
                executor.submit(() -> listenForMessages(socket));
                outputStreams.add(new ObjectOutputStream(socket.getOutputStream()));
            }

			//Message messages[] = {null, null};
			//List<Message> messages = new ArrayList<>();
			while (messagesSent < MESSAGES_TO_SEND) {
                try { Thread.sleep(random.nextInt(10) + 5); } catch(InterruptedException e) {}
                synchronized(mutex) {
                    // Send a message to all other machines
                    nodeVectorClock.increment(nodeID); // Increment my own clock
                    System.out.println("C" + nodeID + ": M" + (messagesSent+1) + " PREPARED, CLOCK UPDATED TO " + nodeVectorClock.toString());
                    //Message message = new Message(nodeID, i+1, nodeVectorClock.clock);
                    Message message = new Message(nodeID, messagesSent+1, nodeVectorClock.copy());
					broadcastMessage(outputStreams, message);
					messagesSent++;
					//messages.add(new Message(nodeID, i+1, nodeVectorClock.copy()));
                    //nodeVectorClock.clock.clone(); // ??
                    //if(!(nodeID == 3 && i == 0)) {
						//broadcastMessage(outputStreams, messages[i]);
						//broadcastMessage(outputStreams, messages.get(i));
					//} else {
					//	try { Thread.sleep(1000); } catch(InterruptedException e) {}
					//}
                }
            }
			/*try { Thread.sleep(1000); } catch(InterruptedException e) {}
			if(nodeID == 3) {
				synchronized(mutex) {
					//broadcastMessage(outputStreams, messages[0]);
					broadcastMessage(outputStreams, messages.get(0));
				}
			}*/

			// TODO: probably gonna have to do this in a while loop and also make sure no thread handling sockets is still finishing up
            // Close all connections
			//synchronized (mutex) {
				//if (messagesSent == MESSAGES_TO_SEND && messagesReceived == MESSAGES_TO_SEND * (TOTAL_PROCESSES - 1)) {
					//for (Socket s : sockets) s.close();
				//}
			//}
			
			try { Thread.sleep(250); } catch(InterruptedException e) {}
			System.out.println("\nMessages sent: " + messagesSent);
			System.out.println("Messages received: " + messagesReceived);
			System.out.println("Biggest queue size: " + biggestQueue);
			System.out.println("Total held back: " + totalHeldBack + "/" + MESSAGES_TO_RECEIVE);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    private static void listenForMessages(Socket socket) {
        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
			int mr = 0;
			//while (true) {
			while (mr < MESSAGES_TO_RECEIVE) {
            //while (messagesReceived < MESSAGES_TO_RECEIVE) {
                Message message = (Message) inputStream.readObject();
                // TODO: i think we don't increment index upon receive and only increment index upon deliver rather than full merge
                synchronized(mutex) {
					//messagesReceived++;
                    // TODO: increment clock before send or not?
                    //nodeVectorClock.clock[message.senderID]++;
                    System.out.println("From C" + message.senderID + ": M" + message.messageNumber + " " + message.toString());
                    if(nodeVectorClock.isDeliverable(message)) {
                        //System.out.println("\tDELIVERABLE");
                        nodeVectorClock.clock[message.senderID] = Math.max(nodeVectorClock.clock[message.senderID], message.vectorClock.clock[message.senderID]);
                        System.out.println("\tC" + nodeID + ": CLOCK UPDATED TO " + nodeVectorClock.toString());
						System.out.println("\tWaiting queue size: " + waiting.size());
                        boolean check;
                        do {
                            //for (Message w : waiting) {
                            check = false;
                            for(int i = 0; i < waiting.size(); i++)
                                if(nodeVectorClock.isDeliverable(waiting.get(i))) {
                                    System.out.println("\t\tMessage is now deliverable: M" + waiting.get(i).messageNumber + " from C" + waiting.get(i).senderID);
									//System.out.println("\t\tnodeVectorClock.clock[" + waiting.get(i).senderID + "]: " + nodeVectorClock.clock[waiting.get(i).senderID]);
									//System.out.println("\t\twaiting.get(i).vectorClock.clock[" + waiting.get(i).senderID + "]: " + waiting.get(i).vectorClock.clock[waiting.get(i).senderID]);
                                    //nodeVectorClock.clock[message.senderID] = Math.max(nodeVectorClock.clock[message.senderID], message.vectorClock.clock[message.senderID]);
									nodeVectorClock.clock[waiting.get(i).senderID] = Math.max(nodeVectorClock.clock[waiting.get(i).senderID], waiting.get(i).vectorClock.clock[waiting.get(i).senderID]);
                                    System.out.println("\t\tC" + nodeID + ": CLOCK UPDATED TO " + nodeVectorClock.toString());
                                    waiting.remove(i);
									System.out.println("\tWaiting queue size: " + waiting.size());
                                    check = true;
                                    break;
                                }
                            //}
                        } while(check);
                    } else {
                        System.out.println("\tNOT DELIVERABLE");
                        waiting.add(message);
						totalHeldBack++;
						if (waiting.size() > biggestQueue) biggestQueue = waiting.size();
						System.out.println("\tWaiting queue size: " + waiting.size());
                    }
                    // TODO: figure out if need to full merge or only update at sender index
                    //nodeVectorClock.merge(message.vectorClock);
                    //nodeVectorClock.clock[message.senderID] = Math.max(nodeVectorClock.clock[message.senderID], message.vectorClock[message.senderID]);
                    //System.out.println("\tC" + nodeID + ": clock updated to " + nodeVectorClock.toString());
					messagesReceived++;
					//if (messagesReceived == MESSAGES_TO_RECEIVE) break;
                }
				mr++;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void broadcastMessage(List<ObjectOutputStream> outputStreams, Message message) {
        int i = 0;
        for (ObjectOutputStream outputStream : outputStreams) {
            System.out.println("\tC" + nodeID + ": sending M" + message.senderID + " " + message.toString() + " to C" + listeners.get(i)); i++;
            try {
                outputStream.writeObject(message);
                outputStream.flush();
                try { Thread.sleep(random.nextInt(10) + 5); } catch (InterruptedException e) {e.printStackTrace();}
            } catch (IOException e) {
                e.printStackTrace();
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