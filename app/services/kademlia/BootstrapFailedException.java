package services.kademlia;

/**
 * Exception thrown when boostrap process fails
 */
public class BootstrapFailedException extends Exception {
    public BootstrapFailedException() {
        super();
    }

    public BootstrapFailedException( String msg) {
        super(msg);
    }
}
