package services.kademlia;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Kademlia routing table.
 */
public class RoutingTable {
    private static final int TABLE_LENGTH = 256;

    private final Node localNode;
    private final KBucket[] buckets;

    public RoutingTable(Node localNode) {
        this.localNode = localNode;
        buckets = new KBucket[TABLE_LENGTH + 1];

        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new KBucket(i); // Create buckets
        }

        this.insert(localNode, System.currentTimeMillis());
    }

    /**
     * Inserts a new contact
     * @param n The new contact
     */
    public synchronized void insert(Node n, long timestamp) {
        this.insert(new Contact(n, timestamp));
    }

    /**
     * Inserts a new contact
     * @param c The new contact
     */
    public synchronized void insert(Contact c) {
        //System.out.println("Inserting contact: " + c.toString());
        this.buckets[this.getBucketId(c.getIdentifier())].insert(c);
    }

    /**
     * Determines the bucket based on distance.
     *
     * @param key The kad_key.
     * @return The bucket id.
     */
    public int getBucketId(KadKey key) {
        int bId = this.localNode.getIdentifier().distance(key);

        return bId < 0 ? 0 : bId;
    }

    /**
     * @return List A List of all Nodes in this routing table
     */
    public synchronized final List<Node> getAllNodes()
    {
        List<Node> nodes = new ArrayList<>();

        for (KBucket b : this.buckets)
        {
            for (Contact c : b.getContacts())
            {
                nodes.add(c);
            }
        }

        return nodes;
    }

    /**
     * Find the closest set of contacts to a given NodeId
     *
     * @param target           The NodeId to find contacts close to
     * @param numNodesRequired The number of contacts to find
     * @return List A List of contacts closest to target
     */
    public synchronized final List<Node> findClosest(KadKey target, int numNodesRequired) {
        TreeSet<Node> sortedSet = new TreeSet<>(new DistanceComparator(target));
        sortedSet.addAll(this.getAllNodes());

        List<Node> closest = new ArrayList<>(numNodesRequired);

        /* Now we have the sorted set, lets get the top numRequired */
        int count = 0;
        for (Node n : sortedSet) {
            closest.add(n);
            if (++count == numNodesRequired) {
                break;
            }
        }
        return closest;
    }

    /**
     * @return List A List of all Nodes in this routing table
     */
    public final List<Contact> getAllContacts()
    {
        List<Contact> contacts = new ArrayList<>();

        for (KBucket b : this.buckets)
        {
            contacts.addAll(b.getContacts());
        }

        return contacts;
    }

    /**
     * @return Bucket[] The buckets in this Kad Instance
     */
    public final KBucket[] getBuckets()
    {
        return this.buckets;
    }

    /**
     * Method used by operations to notify the routing table of any contacts that have been unresponsive.
     *
     * @param contacts The set of unresponsive contacts
     */
    public void setUnresponsiveContacts(List<Node> contacts)
    {
        if (contacts.isEmpty())
        {
            return;
        }
        for (Node n : contacts)
        {
            this.setUnresponsiveContact(n);
        }
    }

    /**
     * Method used by operations to notify the routing table of any contacts that have been unresponsive.
     *
     * @param n
     */
    public synchronized void setUnresponsiveContact(Node n)
    {
        //System.out.println("Unresponsive contact: " + n.toString());
        int bucketId = this.getBucketId(n.getIdentifier());

        /* Remove the contact from the bucket */
        this.buckets[bucketId].removeContact(new Contact(n));
    }

    @Override
    public synchronized final String toString()
    {
        StringBuilder sb = new StringBuilder("\nPrinting Routing Table Started ***************** \n");
        int totalContacts = 0;
        for (KBucket b : this.buckets)
        {
            if (b.numContacts() > 0)
            {
                totalContacts += b.numContacts();
                sb.append("# nodes in Bucket with depth ");
                sb.append(b.getDepth());
                sb.append(": ");
                sb.append(b.numContacts());
                sb.append("\n");
                sb.append(b.toString());
                sb.append("\n");
            }
        }

        sb.append("\nTotal Contacts: ");
        sb.append(totalContacts);
        sb.append("\n\n");

        sb.append("Printing Routing Table Ended ******************** ");

        return sb.toString();
    }


}
