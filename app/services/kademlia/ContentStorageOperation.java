package services.kademlia;

import services.DHTService;

import javax.crypto.spec.IvParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;

/**
 * Content storage operation.
 */
public class ContentStorageOperation extends Thread {
    private DHTService dht;
    private Node node;
    private KadKey key;
    private byte[] content;

    public ContentStorageOperation(DHTService dht, Node node, KadKey key, byte[] content) {
        this.dht = dht;
        this.node = node;
        this.key = key;
        this.content = content;
    }

    @Override
    public void run() {
        if (this.node.equals(this.dht.getLocalNode())) {
            // Local node, store locally
            try {
                this.dht.storageService.storeBlockLocal(this.key, this.content);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Send the block to remote node
            // First, connect to it
            Socket s;

            try {
                s = node.openConnection();
                s.setSoTimeout((int)KademliaConfiguration.RESPONSE_TIMEOUT);
            } catch (IOException ex) {
                System.out.println("[ERROR] Could not connect to node " + this.node.toString() + " / Reason: " + ex.getMessage());
                return;
            }

            try {
                DataInputStream input = new DataInputStream(s.getInputStream());
                DataOutputStream output = new DataOutputStream(s.getOutputStream());

                // Receive IV
                byte[] iv = new byte[16];
                input.readFully(iv);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

                // Send STORE
                KadTCPMessage storeMsg = KadTCPMessage.createStoreMessage(this.key, this.content);
                output.write(storeMsg.serializeAndEncrypt(ivParameterSpec));

                s.close(); // Close connection
            } catch (Exception ex) {
                try {
                    s.close();
                } catch (Exception e) {}
            }
        }
    }
}
