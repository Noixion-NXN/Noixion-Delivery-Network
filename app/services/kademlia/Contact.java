package services.kademlia;

/**
 * Represents a contact, a known node.
 */
public class Contact extends Node implements Comparable<Contact> {

    private long lastSeen;

    private int staleCount;

    public Contact(String address, int port) {
        super(address, port);
        this.lastSeen = System.currentTimeMillis();
        this.staleCount = 0;
    }

    public Contact(String address, int port, long timestamp) {
        super(address, port);
        this.lastSeen = timestamp;
        this.staleCount = 0;
    }

    public Contact(Node node) {
        this(node.getAddress(), node.getPort());
    }

    public Contact(Node node, long timestamp) {
        this(node.getAddress(), node.getPort(), timestamp);
    }


    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public int getStaleCount() {
        return staleCount;
    }

    public void setStaleCount(int staleCount) {
        this.staleCount = staleCount;
    }

    /**
     * Increments the stale counter.
     */
    public void incrementStaleCount() {
        this.staleCount++;
    }

    /**
     * Resets the stale counter
     */
    public void resetStaleCount() {
        this.staleCount = 0;
    }

    /**
     * Sets the last seen to NOW
     */
    public void setSeenNow()
    {
        this.lastSeen = System.currentTimeMillis() / 1000L;
    }

    @Override
    public int compareTo(Contact o) {
        if (this.equals(o)) {
            return 0;
        }
        return (this.lastSeen > o.lastSeen) ? 1 : -1;
    }
}
