import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Server {

	ArrayList<ClientThread> clients = new ArrayList<>();
	TheServer server;
	private Consumer<Serializable> callback;
	private int count = 0;

	Server(Consumer<Serializable> call) {
		callback = call;
		server = new TheServer();
		server.start();
	}

	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket mysocket = new ServerSocket(5555);) {
				System.out.println("Server is waiting for a client!");
				while (true) {
					Socket clientSocket = mysocket.accept();
					ClientThread c = new ClientThread(clientSocket, ++count);
					clients.add(c);
					callback.accept("Client #" + count + " has connected to server");
					c.start();
				}
			} catch (Exception e) {
				callback.accept("Server socket did not launch");
			}
		}
	}

	class ClientThread extends Thread {
		Socket connection;
		int count;
		ObjectInputStream in;
		ObjectOutputStream out;
		ClientThread opponent;
		boolean wantsTwoPlayerGame = false;

		ClientThread(Socket s, int count) {
			this.connection = s;
			this.count = count;
		}

		public void setOpponent(ClientThread opponent) {
			this.opponent = opponent;
		}

		public void updateClients(String message) {
			try {
				if (opponent != null) {
					opponent.out.writeObject(message);
				}
			} catch (Exception e) {
				callback.accept("Error sending message to opponent.");
			}
		}

		private void tryPairingTwoPlayers() {
			List<ClientThread> requestingClients = clients.stream()
					.filter(c -> c.wantsTwoPlayerGame && c.opponent == null)
					.collect(Collectors.toList());

			for (int i = 0; i < requestingClients.size() - 1; i += 2) {
				ClientThread client1 = requestingClients.get(i);
				ClientThread client2 = requestingClients.get(i + 1);
				client1.setOpponent(client2);
				client2.setOpponent(client1);
				callback.accept("Client #" + client1.count + " and Client #" + client2.count + " are now paired for a two-player game.");
			}
		}

		private void sendWinMessage() throws IOException {
			out.writeObject("You won");
		}

		private void sendGoodShotMessage() throws IOException {
			out.writeObject("Good shot");
		}

		public void run() {
			try {
				out = new ObjectOutputStream(connection.getOutputStream());
				in = new ObjectInputStream(connection.getInputStream());
				connection.setTcpNoDelay(true);
			} catch (Exception e) {
				System.out.println("Streams not open");
				return;
			}

			callback.accept("New client on server: Client #" + count);

			while (true) {
				try {
					String data = in.readObject().toString();
					callback.accept("Client #" + count + " sent: " + data);
					if(data.startsWith("MOVE")){
						if(opponent!=null){
							opponent.out.writeObject(data);
						}
					}else if ("request_two_player_game".equals(data)) {
						this.wantsTwoPlayerGame = true;
						tryPairingTwoPlayers();
					}else if ("Client lost".equals(data)) {
						this.opponent.sendWinMessage();
					} else if ("Good enemy shot".equals(data)) {
						this.opponent.sendGoodShotMessage();
					}
					else {
						updateClients(data);
					}
				} catch (Exception e) {
					callback.accept("Oops... Something wrong with the socket from client: " + count + "....closing down!");
					updateClients("Client #" + count + " has left the server!");
					clients.remove(this);
					break;
				}
			}
		}
	}
}
