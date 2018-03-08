package com.nullopt;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/*
 * The server that can be run both as a console application or a GUI
 */
@SuppressWarnings({"UnqualifiedInnerClassAccess", "UnnecessarilyQualifiedInnerClassAccess"})
public class Server {

	// a unique ID for each connection
	private static int uniqueId;
	// an ArrayList to keep the list of the Client
	private final ArrayList<ClientThread> al;
	// if I am in a GUI
	private final ServerGUI sg;
	// to display time
	private final SimpleDateFormat sdf;
	// the port number to listen for connection
	private final int port;
	// the boolean that will be turned of to stop the server
	private boolean keepGoing;

	Server(int port, ServerGUI sg) {
		// GUI or not
		this.sg = sg;
		// the port
		this.port = port;
		// to display hh:mm:ss
		this.sdf = new SimpleDateFormat("HH:mm:ss");
		// ArrayList for the Client list
		this.al = new ArrayList<>();
	}

	public void start() {
		this.keepGoing = true;
		/* create socket server and wait for connection requests */
		try {
			// the socket used by the server
			ServerSocket serverSocket = new ServerSocket(this.port);

			// infinite loop to wait for connections
			while (this.keepGoing) {
				// format message saying we are waiting
				this.display("[Server] Waiting for Clients on port " + this.port + "...");
				Socket socket = serverSocket.accept();    // accept connection
				// if I was asked to stop
				if (!this.keepGoing)
					break;
				Server.ClientThread t = new Server.ClientThread(socket);  // make a thread of it
				this.al.add(t);                                    // save it in the ArrayList
				t.spawn();
				t.start();
			}
			// I was asked to stop
			try {
				serverSocket.close();
				for (Server.ClientThread tc : this.al) {
					try {
						tc.sInput.close();
						tc.sOutput.close();
						tc.socket.close();
					} catch (IOException ioE) {
						// not much I can do
					}
				}
			} catch (Exception e) {
				this.display("Exception closing the server and clients: " + e);
			}
		}
		// something went bad
		catch (IOException e) {
			String msg = this.sdf.format(new Date()) + " Exception on new ServerSocket: " + e + "\n";
			this.display(msg);
		}
	}

	/*
	 * For the GUI to stop the server
	 */
	protected void stop() {
		this.keepGoing = false;
		// connect to myself as Client to exit statement
		// Socket socket = serverSocket.accept();
		try {
			new Socket("localhost", this.port);
		} catch (Exception e) {
			// nothing I can really do
		}
	}

	/*
	 * Display an event (not a message) to the console or the GUI
	 */
	private void display(String msg) {
		String time = this.sdf.format(new Date()) + " " + msg;
		if (this.sg == null)
			System.out.println(time);
		else
			this.sg.appendEvent(time + "\n");
	}

	/*
	 *  to broadcast a packet to all Clients
	 */
	private synchronized void broadcast(Packet packet) {

		// loop in reverse order in case we would have to remove a Client
		// because it has disconnected
		for (int i = this.al.size(); --i >= 0; ) {
			Server.ClientThread ct = this.al.get(i);
			ct.sendPacket(packet);
			// try to write to the Client if it fails remove it from the list
			if (!ct.sendPacket(new Packet(Packet.HEARTBEAT, ct.username, ""))) {
				this.al.remove(i);
				this.display("Disconnected Client " + ct.username + " removed from list.");
			}
		}
	}

	// for a client who logoff using the LOGOUT message
	private synchronized void remove(int id) {
		// scan the array list until we found the Id
		for (int i = 0; i < this.al.size(); ++i) {
			Server.ClientThread ct = this.al.get(i);
			// found it
			if (ct.id == id) {
				this.al.remove(i);
				return;
			}
		}
	}

	/**
	 * One instance of this thread will run for each client
	 */
	class ClientThread extends Thread {

		// the socket where to listen/talk
		final Socket socket;
		// my unique id (easier for disconnection)
		final int id;
		ObjectInputStream sInput;
		ObjectOutputStream sOutput;
		// the Username of the Client
		String username;
		// the packet received from the client
		Packet receivedPacket;
		// the date I connect
		String date;

		// Constructor
		ClientThread(Socket socket) {
			// a unique id
			this.id = ++uniqueId;
			this.socket = socket;
			/* Creating both Data Stream */
			System.out.println("Thread trying to create Object Input/Output Streams");
			try {
				// create output first
				this.sOutput = new ObjectOutputStream(socket.getOutputStream());
				this.sInput = new ObjectInputStream(socket.getInputStream());
				// read the username
				this.username = (String) this.sInput.readObject();
				Server.this.display(this.username + " just connected.");
				Server.this.broadcast(new Packet(Packet.NEW_CONNECTION, this.username, "Blue"));
				System.out.println("New Connection.");
			} catch (IOException e) {
				Server.this.display("Exception creating new Input/output Streams: " + e);
				return;
			}
			// have to catch ClassNotFoundException
			// but I read a String, I am sure it will work
			catch (ClassNotFoundException ignored) {
			}
			this.date = new Date() + "\n";
		}

		// what will run forever
		public void run() {
			// to loop until LOGOUT
			boolean keepGoing = true;
			while (keepGoing) {
				// read a String (which is an object)
				try {
					this.receivedPacket = (Packet) this.sInput.readObject();
				} catch (IOException e) {
					Server.this.display(this.username + " Exception reading Streams: " + e);
					break;
				} catch (ClassNotFoundException e2) {
					break;
				}
				// the message part of the Packet
				String message = this.receivedPacket.getMessage();

				// Switch on the type of message receive
				switch (this.receivedPacket.getType()) {
					case Packet.MOVEMENT:
						Server.this.broadcast(new Packet(Packet.MOVEMENT, this.username, this
							.receivedPacket.getKeysHeld(), true));
						//Server.this.display(this.receivedPacket.getKeysHeld());
						//System.out.println(this.receivedPacket.getKeysHeld());
						break;
					case Packet.LOGOUT:
						//Server.this.broadcast(new Packet(Packet.LOGOUT, this.username, message));
						keepGoing = false;
						break;
					case Packet.HEARTBEAT:
						//Server.this.display(this.receivedPacket.getMessage());
						break;
					case Packet.COLLISION_EVENT:
						//Server.this.broadcast(new Packet(Packet.COLLISION_EVENT, this.username,
						//	message));
						break;
					case Packet.NEW_CONNECTION:
						break;
				}
			}
			// remove myself from the arrayList containing the list of the
			// connected Clients
			Server.this.remove(this.id);
			this.close();
		}

		// try to close everything
		private void close() {
			// try to close the connection
			try {
				if (this.sOutput != null) this.sOutput.close();
			} catch (Exception ignored) {
			}
			try {
				if (this.sInput != null) this.sInput.close();
			} catch (Exception ignored) {
			}
			try {
				if (this.socket != null) this.socket.close();
			} catch (Exception ignored) {
			}
		}

		/*
		 * Write a String to the Client output stream
		 */
		private boolean sendPacket(Packet packet) {
			// if Client is still connected send the message to it
			if (!this.socket.isConnected()) {
				this.close();
				return false;
			}
			// write the message to the stream
			try {
				this.sOutput.writeObject(packet);
			}
			// if an error occurs, do not abort just inform the user
			catch (IOException e) {
				Server.this.display("Error sending message to " + this.username);
				Server.this.display(e.toString());
			}
			return true;
		}

		void spawn() {
			this.sendPacket(new Packet(Packet.NEW_CONNECTION, this.username, Server.this.al.size()
				<= 1 ? "Red" : "Blue"));
		}
	}
}


