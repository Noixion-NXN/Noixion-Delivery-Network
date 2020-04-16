package services.kademlia;

import services.DHTService;

import javax.crypto.spec.IvParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * Content recover operation. Recovers a block from other peer.
 */
public class ContentRecoverOperation extends Thread {

    private DHTService dht;
    private KadKey key;
    private Node node;

    public ContentRecoverOperation(DHTService dht, KadKey key, Node node) {
        this.dht = dht;
        this.key = key;
        this.node = node;
    }

    @Override
    public void run() {
        Socket s;

        try {
            s = this.node.openConnection();
            s.setSoTimeout((int)KademliaConfiguration.RESPONSE_TIMEOUT);
        } catch (Exception ex) {
            System.out.println("[WARNING] Could not connect to node " + this.node.toString() + " / Reason: " + ex.getMessage());
            return;
        }

        try {
            DataInputStream input = new DataInputStream(s.getInputStream());
            DataOutputStream output = new DataOutputStream(s.getOutputStream());

            // Receive IV
            byte[] iv = new byte[16];
            input.readFully(iv);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            // Send READ
            KadTCPMessage readMsg = KadTCPMessage.createReadMessage(this.key);
            output.write(readMsg.serializeAndEncrypt(ivParameterSpec));

            // Receive STORE or ERROR
            KadTCPMessage storeMsg = new KadTCPMessage();
            storeMsg.readFromEncryptedStream(input, ivParameterSpec);

            if (storeMsg.isStore() && storeMsg.getRequestKey().equals(this.key)) {
                // Store the message
                dht.storageService.storeBlockLocal(storeMsg.getRequestKey(), storeMsg.getContent());
            } else {
                System.out.println("[ERROR] Block not found when recovering content (Fraudulent announce?). From node: " + this.node.toString());
            }

            s.close(); // Close connection
        } catch (Exception ex) {
            try {
                s.close();
            } catch (Exception e) {}
        }
    }
}
