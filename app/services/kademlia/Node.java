package services.kademlia;

import java.io.IOException;
import java.net.Socket;

/**
 * Represents a known node
 */
public class Node {

    private KadKey identifier;

    private String address;
    private int port;

    public Node(String address, int port) {
        this.address = address;
        this.port = port;
        this.identifier = KadKey.forNode(address, port);
    }

    public Node (String node) {
        int lastColon = node.lastIndexOf(":");
        this.address = node.substring(0, lastColon);
        this.port = Integer.parseInt(node.substring(lastColon + 1));
    }

    /**
     * @return The node identifier
     */
    public KadKey getIdentifier() {
        return identifier;
    }

    /**
     * @return The node IP address or host
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return The node port
     */
    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        if (this.identifier == null) {
            return "PEER[" + this.address + ":" + this.port + "]";
        } else {
            return "PEER[" + this.address + ":" + this.port + "]{" + this.identifier.toString() + "}";
        }

    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Node) {
            return this.identifier.equals(((Node) o).identifier);
        } else {
            return false;
        }
    }

    /**
     * Connects to the node
     * @return The socket
     * @throws IOException
     */
    public Socket openConnection() throws IOException {
        return new Socket(this.address, this.port);
    }

}
