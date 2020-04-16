package services.kademlia;

/**
 * Exception thrown when a block is not found.
 */
public class BlockNotFoundException extends Exception {
    public BlockNotFoundException() {
        super("Block not found in distributed hash table");
    }
}
