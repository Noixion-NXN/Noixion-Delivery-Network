package services.kademlia;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Kademlia TCP message (for sending blocks)
 */
public class KadTCPMessage {

    private static final String CIPHER_ALGORITHM = "AES/CTR/NoPadding";

    private static final int MAX_MSG_SIZE = 20 * 1024 * 1024;

    private static final int MSG_TCP_READ = 0x05;
    private static final int MSG_TCP_STORE = 0x06;
    private static final int MSG_TCP_ERROR = 0x08;

    private int type;

    private KadKey requestKey;
    private byte[] content;

    public KadTCPMessage() {
        this.type = 0;
        this.requestKey = null;
        this.content = null;
    }

    /**
     * Creates a READ message.
     * @param key The block kad_key.
     * @return The message.
     */
    public static KadTCPMessage createReadMessage(KadKey key) {
        KadTCPMessage msg = new KadTCPMessage();
        msg.setType(MSG_TCP_READ);
        msg.setRequestKey(key);
        return msg;
    }

    /**
     * Creates a STORE message.
     * @param key The block kad_key.
     * @param content The block content.
     * @return The message.
     */
    public static KadTCPMessage createStoreMessage(KadKey key, byte[] content) {
        KadTCPMessage msg = new KadTCPMessage();
        msg.setType(MSG_TCP_STORE);
        msg.setRequestKey(key);
        msg.setContent(content);
        return msg;
    }

    /**
     * Creates a ERROR message.
     * @return The message.
     */
    public static KadTCPMessage createErrorMessage() {
        KadTCPMessage msg = new KadTCPMessage();
        msg.setType(MSG_TCP_ERROR);
        return msg;
    }

    /**
     * @return True if the message is a STORE message.
     */
    public boolean isStore() {
        return this.type == MSG_TCP_STORE;
    }

    /**
     * @return True if the message is a READ message.
     */
    public boolean isRead() {
        return this.type == MSG_TCP_READ;
    }

    /**
     * @return True if the message is a ERROR message.
     */
    public boolean isError() {
        return this.type == MSG_TCP_ERROR;
    }

    /**
     * Reads the message from a stream.
     * @param stream The input stream.
     * @throws IOException
     */
    public void readFromStream(DataInputStream stream) throws IOException {
        this.type = stream.readInt();
        switch (this.type) {
            case MSG_TCP_READ:
            {
                byte[] keyBytes = new byte[32];
                stream.readFully(keyBytes);
                this.requestKey = new KadKey(keyBytes);
            }
                break;
            case MSG_TCP_STORE:
            {
                byte[] keyBytes = new byte[32];
                stream.readFully(keyBytes);
                this.requestKey = new KadKey(keyBytes);
                int blockSize = stream.readInt();
                if (blockSize < 0 || blockSize > KademliaConfiguration.MAX_BLOCK_SIZE) {
                    throw new IOException("Invalid block size.");
                }
                this.content = new byte[blockSize];
                stream.readFully(this.content);
                if (!requestKey.validateForContent(this.content)) {
                    throw new IOException("The kad_key received is invalid for the content received.");
                }
            }
                break;
            case MSG_TCP_ERROR:
                break;
            default:
                throw new IOException("Unknown message type.");
        }
    }

    /**
     * Reads a message from an encrypted stream.
     * @param stream The input stream.
     * @param ivParameterSpec The IV for the connection.
     * @throws IOException
     */
    public void readFromEncryptedStream(DataInputStream stream, IvParameterSpec ivParameterSpec) throws IOException {
        int size = stream.readInt();

        if (size < 1) {
            throw new IOException("Invalid protocol.");
        }

        if (size > MAX_MSG_SIZE) {
            throw new IOException("The message received was too big");
        }

        byte[] data = new byte[size];

        // Read data
        stream.readFully(data);

        // Decrypt data
        SecretKey originalKey = new SecretKeySpec(KademliaConfiguration.NETWORK_PROOF_KEY, 0, KademliaConfiguration.NETWORK_PROOF_KEY.length > 32 ? 32 : KademliaConfiguration.NETWORK_PROOF_KEY.length, "AES");
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, originalKey, ivParameterSpec);
            data = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ByteArrayInputStream bin = new ByteArrayInputStream(data, 0, data.length);
        DataInputStream din = new DataInputStream(bin);

        this.readFromStream(din);
    }

    /**
     * @return the serialized message
     */
    public byte[] serialize() {
        int size = 4;
        switch (this.type) {
            case MSG_TCP_READ:
            {
                size += 32;
            }
            break;
            case MSG_TCP_STORE:
            {
               size += 32 + 4 + content.length;
            }
            break;
        }
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(this.type);
        switch (this.type) {
            case MSG_TCP_READ:
            {
                buf.put(requestKey.getBytes());
            }
            break;
            case MSG_TCP_STORE:
            {
                buf.put(requestKey.getBytes());
                buf.putInt(content.length);
                buf.put(content);
            }
            break;
        }

        return buf.array();
    }

    /**
     * @return the serialized and encrypted message.
     */
    public byte[] serializeAndEncrypt(IvParameterSpec ivParameterSpec) {
        byte[] data = this.serialize();


        // Decrypt data
        SecretKey originalKey = new SecretKeySpec(KademliaConfiguration.NETWORK_PROOF_KEY, 0, KademliaConfiguration.NETWORK_PROOF_KEY.length > 32 ? 32 : KademliaConfiguration.NETWORK_PROOF_KEY.length, "AES");
        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, originalKey, ivParameterSpec);
            data = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        ByteBuffer buf = ByteBuffer.allocate(4 + data.length);
        buf.putInt(data.length);
        buf.put(data);

        return buf.array();
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public KadKey getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(KadKey requestKey) {
        this.requestKey = requestKey;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }
}
