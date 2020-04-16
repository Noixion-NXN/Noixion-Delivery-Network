package services.kademlia;

import org.bouncycastle.util.encoders.Hex;
import org.tron.common.crypto.Hash;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Represents a peer-to-peer message, with security.
 */
public class KadUDPMessage {
    private static final String CIPHER_ALGORITHM = "AES/CTR/NoPadding";
    private static final int DATAGRAM_BUFFER_SIZE = 64 * 1024;      // 64KB

    private static final int NO_MSG = 0x00;
    private static final int HELLO_MESSAGE_CODE = 0x01;
    private static final int NODE_LOOKUP_CODE = 0x02;
    private static final int NODE_REPLY_CODE = 0x03;
    private static final int CONTENT_ANNOUNCE = 0x04;

    private int msgType; // Message type
    private int commId; // Communication identifier (positive for requests, negative for responses)
    private Node origin;
    private long timestamp;
    private byte[] iv; // 16 bytes
    private byte[] proof; //  Network proof (32 bytes)

    private KadKey lookup;
    private List<Node> nodes;

    /**
     * Creates a HELLO message.
     * @param origin Origin, for getting a reply
     * @param commId The communication identifier
     * @return The message
     */
    public static KadUDPMessage createHelloMessage(Node origin, int commId) {
        KadUDPMessage message = new KadUDPMessage(origin, HELLO_MESSAGE_CODE, commId, null, null);
        message.makeProof();
        return message;
    }

    /**
     * Creates a lookup request message.
     * @param origin Origin, for getting a reply
     * @param commId The communication identifier
     * @param lookup The lookup kad_key.
     * @return The message
     */
    public static KadUDPMessage createLookupMessage(Node origin, int commId, KadKey lookup) {
        KadUDPMessage message = new KadUDPMessage(origin, NODE_LOOKUP_CODE, commId, lookup, null);
        message.makeProof();
        return message;
    }

    /**
     * Creates message for announcing a content.
     * @param origin Origin, for getting a reply
     * @param commId The communication identifier
     * @param key The kad_key to announce.
     * @return The message
     */
    public static KadUDPMessage createAnnounceMessage(Node origin, int commId, KadKey key) {
        KadUDPMessage message = new KadUDPMessage(origin, CONTENT_ANNOUNCE, commId, key, null);
        message.makeProof();
        return message;
    }

    /**
     * Creates a lookup reply message.
     * @param origin Origin, for getting a reply
     * @param commId The communication identifier
     * @param nodes The lookup kad_key.
     * @return The message
     */
    public static KadUDPMessage createLookupReplyMessage(Node origin, int commId, List<Node> nodes) {
        KadUDPMessage message = new KadUDPMessage(origin, NODE_REPLY_CODE, commId, null, nodes);
        message.makeProof();
        return message;
    }

    public KadUDPMessage(Node origin, int msgType, int commId, KadKey lookup, List<Node> nodes) {
        this.origin = origin;
        this.msgType = msgType;
        this.commId = commId;
        this.lookup = lookup;
        this.nodes = nodes;
        this.iv = null;
        this.proof = null;
        this.timestamp = System.currentTimeMillis();
    }

    public KadUDPMessage() {
        this.origin = null;
        this.msgType = NO_MSG;
        this.commId = 0;
        this.lookup = null;
        this.nodes = null;
        this.iv = null;
        this.proof = null;
        this.timestamp = 0;
    }


    /**
     * Read the message from an input stream.
     * @param stream The input stream.
     * @throws IOException
     */
    public void readFromStream(DataInputStream stream) throws IOException {
        this.msgType = stream.readInt();

        if (msgType < 0x00 || msgType > 0x04) {
            throw new IOException("Invalid message type received.");
        }

        this.commId = stream.readInt();
        String addr;
        byte[] addrBytes;
        int port, addrLen;

        addrLen = stream.readInt();
        if (addrLen < 0 || addrLen > 128) {
            throw new IOException("Invalid address length. Must be between 0 and 128");
        }
        addrBytes = new byte[addrLen];
        stream.readFully(addrBytes);
        addr = new String(addrBytes, "UTF-8");
        port = stream.readInt();

        origin = new Node(addr, port);

        timestamp = stream.readLong();

        this.iv = new byte[16];
        stream.readFully(this.iv);

        this.proof = new byte[32];
        stream.readFully(this.proof);

        switch (this.msgType) {
            case NODE_LOOKUP_CODE:
            case CONTENT_ANNOUNCE: {
                byte[] keyLookup = new byte[32];
                stream.readFully(keyLookup);
                this.lookup = new KadKey(keyLookup);
            }
            break;
            case NODE_REPLY_CODE: {
                int nodesCount = stream.readInt();
                if (nodesCount < 0 || nodesCount > 255) {
                    throw new IOException("Invalid nodes list size. Must be between 0 and 255.");
                }
                this.nodes = new ArrayList<>();
                for (int i = 0; i < nodesCount; i++) {
                    addrLen = stream.readInt();
                    if (addrLen < 0 || addrLen > 128) {
                        throw new IOException("Invalid address length. Must be between 0 and 128");
                    }
                    addrBytes = new byte[addrLen];
                    stream.readFully(addrBytes);
                    addr = new String(addrBytes, "UTF-8");
                    port = stream.readInt();
                    this.nodes.add(new Node(addr, port));
                }
            }
        }
    }

    /**
     * Serializes the message.
     * @return The serialized message.
     */
    public byte[] serialize() {
        int msgSize = 4 + 4 + 4 + this.origin.getAddress().getBytes().length + 4 + 8 + 16 + 32;
        switch (this.msgType) {
            case NODE_LOOKUP_CODE:
            case CONTENT_ANNOUNCE:{
                msgSize += 32;
            }
            break;
            case NODE_REPLY_CODE: {
                msgSize += 4;
                for (Node n : this.nodes) {
                    msgSize += 4; // Address size
                    msgSize += n.getAddress().getBytes().length; // Address
                    msgSize += 4; // Port
                }
            }
        }
        ByteBuffer buf = ByteBuffer.allocate(msgSize);
        buf.putInt(msgType);
        buf.putInt(commId);

        buf.putInt(this.origin.getAddress().getBytes().length);
        buf.put(this.origin.getAddress().getBytes());
        buf.putInt(this.origin.getPort());

        buf.putLong(this.timestamp);

        buf.put(iv);
        buf.put(proof);

        switch (this.msgType) {
            case NODE_LOOKUP_CODE:
            case CONTENT_ANNOUNCE: {
                buf.put(this.lookup.getBytes());
            }
            break;
            case NODE_REPLY_CODE: {
                buf.putInt(this.nodes.size());
                for (Node n : this.nodes) {
                    buf.putInt(n.getAddress().getBytes().length);
                    buf.put(n.getAddress().getBytes());
                    buf.putInt(n.getPort());
                }
            }
        }

        return buf.array();
    }

    /**
     * Computes the message hash.
     * @return The message hash.
     */
    public byte[] computeHash() {
        int msgSize = 4 + 4 + 4 + this.origin.getAddress().getBytes().length + 4 + 8 + 16;
        switch (this.msgType) {
            case NODE_LOOKUP_CODE:
            case CONTENT_ANNOUNCE: {
                msgSize += 32;
            }
            break;
            case NODE_REPLY_CODE: {
                msgSize += 4;
                for (Node n : this.nodes) {
                    msgSize += 4; // Address size
                    msgSize += n.getAddress().getBytes().length; // Address
                    msgSize += 4; // Port
                }
            }
        }
        ByteBuffer buf = ByteBuffer.allocate(msgSize);
        buf.putInt(msgType);
        buf.putInt(commId);

        buf.putInt(this.origin.getAddress().getBytes().length);
        buf.put(this.origin.getAddress().getBytes());
        buf.putInt(this.origin.getPort());

        buf.putLong(this.timestamp);

        buf.put(iv);

        switch (this.msgType) {
            case NODE_LOOKUP_CODE:
            case CONTENT_ANNOUNCE: {
                buf.put(this.lookup.getBytes());
            }
            break;
            case NODE_REPLY_CODE: {
                buf.putInt(this.nodes.size());
                for (Node n : this.nodes) {
                    buf.putInt(n.getAddress().getBytes().length);
                    buf.put(n.getAddress().getBytes());
                    buf.putInt(n.getPort());
                }
            }
        }

        return Hash.sha3(buf.array());
    }

    /**
     * Creates a proof for this message to be accepted by other peers.
     */
    public void makeProof() {
        SecretKey originalKey = new SecretKeySpec(KademliaConfiguration.NETWORK_PROOF_KEY, 0, KademliaConfiguration.NETWORK_PROOF_KEY.length > 32 ? 32 : KademliaConfiguration.NETWORK_PROOF_KEY.length, "AES");

        this.iv = new byte[16];
        (new SecureRandom()).nextBytes(this.iv);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(this.iv);

        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, originalKey, ivParameterSpec);
            this.proof = cipher.doFinal(this.computeHash());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * Checks if the message is probed to be part of the network.
     * @return True if the message is valid, false if it is invalid.
     */
    public boolean isValid() {
        SecretKey originalKey = new SecretKeySpec(KademliaConfiguration.NETWORK_PROOF_KEY, 0, KademliaConfiguration.NETWORK_PROOF_KEY.length > 32 ? 32 : KademliaConfiguration.NETWORK_PROOF_KEY.length, "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(this.iv);

        byte[] hash = this.computeHash();

        Cipher cipher;
        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, originalKey, ivParameterSpec);
            byte[] resolvedProof = cipher.doFinal(this.proof);
            return Arrays.equals(hash, resolvedProof);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends the message through a socket.
     *
     * @param socket The socket
     * @throws IOException
     */
    public void send(DatagramSocket socket, Node destination) throws IOException {
        //System.out.println("Sending packet..." + this.toString());
        byte[] toSend;
        toSend = this.serialize();
        DatagramPacket pkt = new DatagramPacket(toSend, 0, toSend.length);
        pkt.setAddress(InetAddress.getByName(destination.getAddress()));
        pkt.setPort(destination.getPort());
        socket.send(pkt);
    }

    public boolean isReply() {
        return this.commId < 0;
    }

    /**
     * @return True if it is a HELLO message.
     */
    public boolean isHello() {
        return msgType == HELLO_MESSAGE_CODE;
    }

    /**
     * @return True if it is a LOOKUP message.
     */
    public boolean isLookup() {
        return msgType == NODE_LOOKUP_CODE;
    }

    /**
     * @return True if it is an ANNOUNCE message.
     */
    public boolean isAnnounce() {
        return msgType == CONTENT_ANNOUNCE;
    }

    /**
     * @return True if it is a LOOKUP_REPLY message.
     */
    public boolean isLookupReply() {
        return msgType == NODE_REPLY_CODE;
    }

    public int getMsgType() {
        return msgType;
    }

    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }

    public int getCommId() {
        return Math.abs(commId);
    }

    public void setCommId(int commId) {
        this.commId = commId;
    }

    public Node getOrigin() {
        return origin;
    }

    public void setOrigin(Node origin) {
        this.origin = origin;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public byte[] getProof() {
        return proof;
    }

    public void setProof(byte[] proof) {
        this.proof = proof;
    }

    public KadKey getLookup() {
        return lookup;
    }

    public void setLookup(KadKey lookup) {
        this.lookup = lookup;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    @Override
    public String toString() {
        String str = "\n";

        switch (this.msgType) {
            case NO_MSG:
                str += "Type: NO_MSG";
                break;
            case HELLO_MESSAGE_CODE:
                str += "Type: HELLO";
                break;
            case NODE_LOOKUP_CODE:
                str += "Type: NODE_LOOKUP";
                break;
            case NODE_REPLY_CODE:
                str += "Type: NODE_REPLY";
                break;
            case CONTENT_ANNOUNCE:
                str += "Type: CONTENT_ANNOUNCE";
                break;
            default:
                str += "Type: UNKNOWN";
        }

        str += "\n";

        str += "COMM-ID: " + this.commId;
        str += "\n";
        str += "Origin: " + this.origin.toString();
        str += "\n";
        str += "Timestamp: " + new Date(this.timestamp).toString();
        str += "\n";
        str += "IV: " + Hex.toHexString(this.iv);
        str += "\n";
        str += "Proof: " + Hex.toHexString(this.proof);
        str += "\n";

        switch (this.msgType) {
            case NODE_LOOKUP_CODE:
            case CONTENT_ANNOUNCE: {
                str += "Key: " + this.lookup.toString();
                str += "\n";
            }
            break;
            case NODE_REPLY_CODE: {
                str += "Nodes: (" + this.nodes.size() + ")";
                str += "\n";
                for (Node n : this.nodes) {
                    str += "    > " + n.toString();
                    str += "\n";
                }
            }
        }

        return str;
    }
}
