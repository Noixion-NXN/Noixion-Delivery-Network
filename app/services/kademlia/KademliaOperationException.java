package services.kademlia;

/**
 * Exception thrown when a n operation fails.
 */
public class KademliaOperationException extends Exception {
    public KademliaOperationException() {
        super();
    }

    public KademliaOperationException(String msg) {
        super(msg);
    }
}
