package services.kademlia;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * Compares the distance to a specific kad_key.
 */
public class DistanceComparator implements Comparator<Node> {

    private final BigInteger key;

    public DistanceComparator(KadKey key) {
        this.key = key.getBigInteger();
    }

    @Override
    public int compare(Node o1, Node o2) {
        BigInteger b1 = o1.getIdentifier().getBigInteger();
        BigInteger b2 = o2.getIdentifier().getBigInteger();

        b1 = b1.xor(key);
        b2 = b2.xor(key);

        return b1.abs().compareTo(b2.abs());
    }
}
