package services.kademlia;

import services.DHTService;

import javax.crypto.spec.IvParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;

/**
 * Connection handler. For STORE and GET operations.
 */
public class KademliaConnectionHandler extends Thread {

    private final DHTService dht;
    private final Socket socket;

    public KademliaConnectionHandler(DHTService dht, Socket socket) {
        this.dht = dht;
        this.socket = socket;
    }

    /**
     * Handles the connection.
     * Only 1 operation per connection, to avoid starvation.
     * @throws IOException
     */
    public void handleConnection() throws IOException {
        // Once connected, get the streams
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        // Generate new IV and send it
        byte[] iv = new byte[16];
        (new SecureRandom()).nextBytes(iv);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        output.write(iv, 0, 16);

        KadTCPMessage msg = new KadTCPMessage();

        // Read message
        msg.readFromEncryptedStream(input, ivParameterSpec);

        if (msg.isRead()) {
            if (dht.storageService.hasBlockLocal(msg.getRequestKey())) {
                // Send STORE
                KadTCPMessage reply = KadTCPMessage.createStoreMessage(msg.getRequestKey(), dht.storageService.getBlockLocal(msg.getRequestKey()));
                output.write(reply.serializeAndEncrypt(ivParameterSpec));
            } else {
                // Send error
                KadTCPMessage reply = KadTCPMessage.createErrorMessage();
                output.write(reply.serializeAndEncrypt(ivParameterSpec));
            }
        } else if (msg.isStore()) {
            // Store the block if we do not have it.
            if (!dht.storageService.hasBlockLocal(msg.getRequestKey())) {
                dht.storageService.storeBlockLocal(msg.getRequestKey(), msg.getContent());
            }
        } else {
            throw new IOException("Unexpected message.");
        }
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout((int) KademliaConfiguration.OPERATION_TIMEOUT);
            handleConnection();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            socket.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
