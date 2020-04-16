package services.kademlia;

import services.DHTService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NodeLookupOperation implements KadMessageReceiver {
    private static final int NODE_STATUS_UNASKED = 0;
    private static final int NODE_STATUS_AWAITING = 1;
    private static final int NODE_STATUS_ASKED = 2;
    private static final int NODE_STATUS_FAILED = 3;

    private final DHTService dht;
    private final KadKey key;

    private final Map<Node, Integer> nodes;
    private final DistanceComparator comparator;
    private final Map<Integer, Node> messagesTransiting;

    private boolean finished;
    private final Semaphore semaphore;
    private boolean timedOut;



    public NodeLookupOperation(DHTService dht, KadKey key) {
        this.timedOut = false;
        this.finished = false;
        this.dht = dht;
        this.key = key;
        this.comparator = new DistanceComparator(key);
        this.nodes = new TreeMap<>(this.comparator);
        this.semaphore = new Semaphore(0);
        this.messagesTransiting = new TreeMap<>();
    }

    public void addNodes(List<Node> list)
    {
        for (Node o : list)
        {
            /* If this node is not in the list, add the node */
            if (!nodes.containsKey(o))
            {
                nodes.put(o, NODE_STATUS_UNASKED);
            }
        }
    }

    private List<Node> closestNodes(Integer status)
    {
        List<Node> closestNodes = new ArrayList<>(KademliaConfiguration.K);
        int remainingSpaces = KademliaConfiguration.K;

        for (Map.Entry e : this.nodes.entrySet())
        {
            if (status.equals(e.getValue()))
            {
                /* We got one with the required status, now add it */
                closestNodes.add((Node) e.getKey());
                if (--remainingSpaces == 0)
                {
                    break;
                }
            }
        }

        return closestNodes;
    }

    private List<Node> closestNodesNotFailed(Integer status)
    {
        List<Node> closestNodes = new ArrayList<>(KademliaConfiguration.K);
        int remainingSpaces = KademliaConfiguration.K;

        for (Map.Entry<Node, Integer> e : this.nodes.entrySet())
        {
            if (e.getValue() != NODE_STATUS_FAILED)
            {
                if (status.equals(e.getValue()))
                {
                    /* We got one with the required status, now add it */
                    closestNodes.add(e.getKey());
                }

                if (--remainingSpaces == 0)
                {
                    break;
                }
            }
        }

        return closestNodes;
    }


    public void execute() {
        /* Add all nodes */
        this.addNodes(dht.getRoutingTable().getAllNodes());

        /* Local node already asked, we are online if we are executing this */
        this.nodes.put(dht.getLocalNode(), NODE_STATUS_ASKED);

        /* Start */
        this.askNodesOrFinish();
    }

    public void finalize() {
        if (finished) return;
        finished = true;
        semaphore.release();
    }

    public synchronized void askNodesOrFinish() {
        if (timedOut) {
            return; // Timed out, no reason to continue
        }
        if (finished) {
            return;
        }
        if (this.messagesTransiting.size() >= KademliaConfiguration.CONCURRENCY) {
            // Too many messages sent right now, wait
            return;
        }

        List<Node> unasked = closestNodesNotFailed(NODE_STATUS_UNASKED);

        //System.out.println("Unasked nodes:");
        //for (Node n : unasked) {
           // System.out.println(" - " + n.toString());
       // }

        if (unasked.isEmpty() && this.messagesTransiting.isEmpty()) {
            // Finish!
            this.finalize();
            return;
        }

        for (int i = 0; (this.messagesTransiting.size() < KademliaConfiguration.CONCURRENCY) && (i < unasked.size()); i++) {
            Node node = unasked.get(i);

            int commId = dht.getNextCommId();

            // Status is now awaiting
            this.nodes.put(node, NODE_STATUS_AWAITING);

            // Add message
            messagesTransiting.put(commId, node);

            try {
                dht.sendMessageWithAsyncReply(node, KadUDPMessage.createLookupMessage(dht.getLocalNode(), commId, this.key), this);
            } catch (IOException e) {
                // Set failed
                nodes.put(node, NODE_STATUS_FAILED);

                // Set unresponsive
                dht.getRoutingTable().setUnresponsiveContact(node);

                // Remove from messages transiting
                messagesTransiting.remove(commId);
            }
        }
    }


    public List<Node> waitForResult(long timeout) throws InterruptedException, TimeoutException {
        if (!this.semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
            // Timed out
            this.timedOut = true;
            throw new TimeoutException();
        }
        return closestNodes(NODE_STATUS_ASKED);
    }

    @Override
    public synchronized void receive(KadUDPMessage message) {
        this.messagesTransiting.remove(message.getCommId());

        //System.out.println("Receiving YES");

        if (!message.isLookupReply()) {
            return; // Not the message we are looking for
        }

        /* Insert the node into the routing table */
        dht.getRoutingTable().insert(message.getOrigin(), message.getTimestamp());

        // Confirmed the node who replied to us
        this.nodes.put(message.getOrigin(), NODE_STATUS_ASKED);

        // Add the nodes we received from the peer
        this.addNodes(message.getNodes());

        // Continue
        this.askNodesOrFinish();
    }

    @Override
    public synchronized void timeout(int commId) {
        Node n = this.messagesTransiting.get(commId);
        this.messagesTransiting.remove(commId);

        // Set failed
        nodes.put(n, NODE_STATUS_FAILED);

        // Set unresponsive
        dht.getRoutingTable().setUnresponsiveContact(n);

        // Continue
        this.askNodesOrFinish();
    }
}
