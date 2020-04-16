package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.KadBlock;
import models.VideoUploadStatus;
import org.tron.common.crypto.Hash;
import play.api.Play;
import play.inject.ApplicationLifecycle;
import services.kademlia.*;
import services.videos.VideoIndex;
import services.videos.VideoResolutionIndex;
import utils.StorageConfiguration;
import utils.StoragePaths;

import javax.crypto.spec.IvParameterSpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * DHT/Kademlia service for peer-to-peer networking.
 * Connects to other peers.
 * Overlay routing.
 * Keys and nodes lookup.
 */
@Singleton
public class DHTService {
    private static final int DATAGRAM_BUFFER_SIZE = 64 * 1024;      // 64KB

    @Inject
    public StorageCacheService cacheService;

    @Inject
    public StorageService storageService;

    @Inject
    public S3StorageService s3StorageService;

    @Inject
    public IPFSStorageService ipfsStorageService;

    @Inject
    public BTFSStorageService btfsStorageService;

    private Node localNode;
    private final RoutingTable routingTable;
    private List<Node> seedNodes;

    private int commId;

    private final Map<Integer, Semaphore> waitingResponses;
    private final Map<Integer, KadMessageReceiver> receivers;
    private final Map<Integer, TimerTask> timeouts;
    private final Map<Integer, KadUDPMessage> responses;
    private final Map<KadKey, ContentRecoverOperation> recoverOperations;

    private final Timer timer;

    private ServerSocket serverTCP;
    private DatagramSocket serverUDP;

    private Thread tcpServerThread;
    private Thread udpServerThread;

    private ThreadPoolExecutor contentRecoverExecutor;

    private boolean ended;

    private boolean publishing;
    private boolean doingPurge;

    private boolean s3;
    private boolean ipfs;
    private boolean btfs;

    @Inject
    public DHTService(ApplicationLifecycle lifecycle, StorageModeService storageModeService) throws IOException {
        StorageConfiguration.load();
        this.ended = false;
        this.publishing = false;
        this.doingPurge = false;
        this.commId = 0;
        this.waitingResponses = new TreeMap<>();
        this.responses = new TreeMap<>();
        this.receivers = new TreeMap<>();
        this.timeouts = new TreeMap<>();
        this.recoverOperations = new TreeMap<>();

        this.timer = new Timer(true);

        // Load configuration
        Config config = ConfigFactory.load();
        KademliaConfiguration.CONCURRENCY = config.getInt("kademlia.concurrency");
        KademliaConfiguration.K = config.getInt("kademlia.k");
        KademliaConfiguration.OPERATION_TIMEOUT = config.getInt("kademlia.operation.timeout");
        KademliaConfiguration.RCSIZE = config.getInt("kademlia.rcache.size");
        KademliaConfiguration.RESTORE_INTERVAL = config.getInt("kademlia.restore.interval");
        KademliaConfiguration.RESPONSE_TIMEOUT = config.getInt("kademlia.response.timeout");
        KademliaConfiguration.STALE = config.getInt("kademlia.stale");
        KademliaConfiguration.MAX_BLOCK_SIZE = config.getBytes("kademlia.block.size");
        KademliaConfiguration.REPLICATION = config.getInt("kademlia.replication");

        KademliaConfiguration.NETWORK_PROOF_KEY = Hash.sha3(config.getString("registration.kad_key").getBytes());

        contentRecoverExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(KademliaConfiguration.CONCURRENCY);

        localNode = new Node(config.getString("kademlia.local.address"), config.getInt("kademlia.local.port"));
        this.seedNodes = config.getList("kademlia.seed.nodes").unwrapped().stream().map(n -> new Node(n.toString())).collect(Collectors.toList());

        this.routingTable = new RoutingTable(localNode);

        s3 = false;
        ipfs = false;
        btfs = false;

        if (storageModeService.isS3()) {
            s3 = true;
        } else if (storageModeService.isIPFS()) {
            ipfs = true;
        } else if (storageModeService.isBTFS()) {
            btfs = true;
        } else {
            lifecycle.addStopHook(() -> {
                shutdown();
                return CompletableFuture.completedFuture(null);
            });

            InetSocketAddress bindAddress;

            String bindStr = config.getString("kademlia.server.address");
            int port = config.getInt("kademlia.server.port");
            if (bindStr.length() > 0) {
                bindAddress = new InetSocketAddress(bindStr, port);
            } else {
                bindAddress = new InetSocketAddress(port);
            }

            serverTCP = new ServerSocket(bindAddress.getPort(), 0, bindAddress.getAddress());
            serverUDP = new DatagramSocket(bindAddress);

            System.out.println("Kademlia server started on " + bindAddress + "!");

            this.udpServerThread = new Thread(() -> udpServer());
            this.udpServerThread.start();

            this.tcpServerThread = new Thread(() -> tcpServer());
            this.tcpServerThread.start();

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    new Thread(() -> {
                        try {
                            boostrapWithSeedNodes();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();

                    new Thread(() -> {
                        try {
                            publishDHT();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();
                }
            }, KademliaConfiguration.RESTORE_INTERVAL, KademliaConfiguration.RESTORE_INTERVAL);

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    new Thread(() -> {
                        try {
                            purgeStorage();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();
                }
            }, KademliaConfiguration.PURGE_INTERVAL, KademliaConfiguration.PURGE_INTERVAL);

            new Thread(() -> {
                boostrapWithSeedNodes();
            }).start();

        }
    }

    public RoutingTable getRoutingTable() {
        return this.routingTable;
    }

    public Node getLocalNode() {
        return this.localNode;
    }

    public void boostrapWithSeedNodes() {
        boolean boostrapped = false;
        for (Node seed : this.seedNodes) {
            try {
                this.bootstrap(seed);
            } catch (BootstrapFailedException e) {
                e.printStackTrace();
                continue;
            }
            boostrapped = true;
            System.out.println("Successfully bootstrapped with the node " + seed.toString());
            break;
        }
        if (!boostrapped) {
            refreshBuckets();
        }
    }

    /**
     * @return A new communication id to use.
     */
    public synchronized int getNextCommId() {
        this.commId++;
        if (this.commId < 0) {
            this.commId = 1;
        }
        return this.commId;
    }

    /**
     * Creates a new semaphore for waiting for a reply.
     *
     * @param commId The communication id.
     * @return The semaphore.
     */
    public synchronized Semaphore startWaitingFor(int commId) {
        Semaphore sem = new Semaphore(0);
        waitingResponses.put(commId, sem);
        return sem;
    }

    /**
     * Stop waiting (timeout)
     *
     * @param commId The communication id.
     */
    public synchronized void stopWaitingFor(int commId) {
        waitingResponses.remove(commId);
        responses.remove(commId);
    }

    /**
     * Setupts a received for receiving a message async.
     *
     * @param commId   The communication id.
     * @param receiver The receiver ID.
     * @param timeout
     */
    public synchronized void receiveAsync(int commId, KadMessageReceiver receiver, TimerTask timeout) {
        receivers.put(commId, receiver);
        timeouts.put(commId, timeout);
    }

    public synchronized void cancelReceiveAsync(int commId) {
        System.out.println("Timed out: " + commId);
        receivers.remove(commId);
        if (timeouts.containsKey(commId)) {
            timeouts.get(commId).cancel();
            timeouts.remove(commId);
        }
    }

    /**
     * Provides a response.
     *
     * @param commId   The communication id.
     * @param response The response.
     * @return True if the message was a valid response, false otherwise.
     */
    public synchronized boolean provideResponse(int commId, KadUDPMessage response) {
        if (receivers.containsKey(commId)) {
            final KadMessageReceiver receiver = receivers.get(commId);
            new Thread(() -> {
                receiver.receive(response);
            }).start();

            receivers.remove(commId);
            if (timeouts.containsKey(commId)) {
                timeouts.get(commId).cancel();
                timeouts.remove(commId);
            }
            return true;
        } else if (waitingResponses.containsKey(commId)) {
            responses.put(commId, response);
            waitingResponses.get(commId).release();
            waitingResponses.remove(commId);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Retrieves the provided response.
     *
     * @param commId The communication id.
     * @return The response.
     */
    public synchronized KadUDPMessage getResponse(int commId) {
        KadUDPMessage response = responses.get(commId);
        responses.remove(commId);
        return response;
    }

    /**
     * Provides an asynchronous timeout.
     *
     * @param commId The communication id.
     */
    public synchronized void provideAsyncResponseTimeout(int commId) {
        if (receivers.containsKey(commId)) {
            receivers.get(commId).timeout(commId);
            receivers.remove(commId);
        }
        timeouts.remove(commId);
    }

    /**
     * Starts a content recover operation.
     *
     * @param key  The block kad_key
     * @param node The node that announces the block.
     */
    public synchronized void startContentRecover(KadKey key, Node node) {
        if (!this.recoverOperations.containsKey(key)) {
            ContentRecoverOperation op = new ContentRecoverOperation(this, key, node);
            this.recoverOperations.put(key, op);
            contentRecoverExecutor.execute(op);
        }
    }

    /**
     * Finalizes a content recover operation.
     *
     * @param key Th block kad_key
     */
    public synchronized void finishContentRecover(KadKey key) {
        this.recoverOperations.remove(key);
    }


    /**
     * TCP server.
     */
    public void tcpServer() {
        while (!ended) {
            try {
                Socket s = serverTCP.accept();

                KademliaConnectionHandler handler = new KademliaConnectionHandler(this, s);
                handler.start();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * UDP server.
     */
    public void udpServer() {
        while (!ended) {
            try {
                /* Wait for a packet */
                byte[] buffer = new byte[DATAGRAM_BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverUDP.receive(packet);

                ByteArrayInputStream bin = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
                DataInputStream din = new DataInputStream(bin);

                KadUDPMessage msg = new KadUDPMessage();
                msg.readFromStream(din);
                din.close();

                //System.out.println("[DEBUG] Received packet: " + msg.toString());

                if (!msg.isValid()) {
                    System.out.println("[WARNING] Received invalid message from " + packet.getAddress().toString() + ":" + packet.getPort());
                    continue;
                }

                if (msg.isReply()) {
                    if (!this.provideResponse(msg.getCommId(), msg)) {
                        System.out.println("[WARNING] Received invalid / timed-out response from " + packet.getAddress().toString() + ":" + packet.getPort());
                    }
                } else {
                    if (msg.isHello()) {
                        /* Hello received */
                        this.routingTable.insert(msg.getOrigin(), msg.getTimestamp());

                        if (!msg.isReply()) {
                            // Reply to the origin with other hello
                            KadUDPMessage replyHello = KadUDPMessage.createHelloMessage(localNode, (-1) * msg.getCommId());
                            this.sendMessage(msg.getOrigin(), replyHello);
                        }
                    } else if (msg.isLookup() && !msg.isReply()) {
                        /* Lookup request received */
                        this.routingTable.insert(msg.getOrigin(), msg.getTimestamp());
                        List<Node> closestNodes = this.routingTable.findClosest(msg.getLookup(), KademliaConfiguration.K);
                        KadUDPMessage replyLookup = KadUDPMessage.createLookupReplyMessage(localNode, (-1) * msg.getCommId(), closestNodes);
                        this.sendMessage(msg.getOrigin(), replyLookup);
                    } else if (msg.isAnnounce()) {
                        if (!this.storageService.hasBlockLocal(msg.getLookup())) {
                            // Start operation for recovering the content
                            this.startContentRecover(msg.getLookup(), msg.getOrigin());
                        }
                    } else {
                        System.out.println("[WARNING] Dropped unexpected message received from " + packet.getAddress().toString() + ":" + packet.getPort());
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Sends a message to other peer.
     *
     * @param destination The destination peer.
     * @param msg         The message to send
     * @throws IOException
     */
    public void sendMessage(Node destination, KadUDPMessage msg) throws IOException {
        msg.send(this.serverUDP, destination);
    }

    /**
     * Send s a message to other peer and waits for response.
     *
     * @param destination The destination peer
     * @param msg         The reply
     * @return The response
     * @throws IOException
     * @throws InterruptedException
     */
    public KadUDPMessage sendMessageAndWaitForReply(Node destination, KadUDPMessage msg) throws IOException, InterruptedException {
        Semaphore sem = this.startWaitingFor(msg.getCommId());
        msg.send(this.serverUDP, destination);
        try {
            if (sem.tryAcquire(KademliaConfiguration.RESPONSE_TIMEOUT, TimeUnit.MILLISECONDS)) {
                return this.getResponse(msg.getCommId());
            } else {
                this.stopWaitingFor(msg.getCommId());
                throw new IOException("Response Timed-out");
            }
        } catch (InterruptedException e) {
            this.stopWaitingFor(msg.getCommId());
            throw e;
        }
    }

    /**
     * Sends a message and sets a receiver for it.
     *
     * @param destination The destination node
     * @param msg         The message
     * @param receiver    The receiver for the reply
     * @throws IOException
     */
    public void sendMessageWithAsyncReply(Node destination, KadUDPMessage msg, KadMessageReceiver receiver) throws IOException {
        // Setup receiver and timeout task
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                new Thread(() -> {
                    try {
                        provideAsyncResponseTimeout(msg.getCommId());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        };
        this.receiveAsync(msg.getCommId(), receiver, task);
        timer.schedule(task, KademliaConfiguration.OPERATION_TIMEOUT);
        try {
            msg.send(this.serverUDP, destination);
        } catch (IOException e) {
            this.cancelReceiveAsync(msg.getCommId());
            throw e;
        }
    }

    /**
     * Send s a hello message and waits for reply.
     *
     * @param destination The destination peer.
     * @return The hello reply
     * @throws IOException
     * @throws InterruptedException
     */
    public KadUDPMessage sendHello(Node destination) throws IOException, InterruptedException {
        return this.sendMessageAndWaitForReply(destination, KadUDPMessage.createHelloMessage(localNode, this.getNextCommId()));
    }

    /**
     * Sends a lookup request
     *
     * @param destination
     * @param key
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public KadUDPMessage sendLookup(Node destination, KadKey key) throws IOException, InterruptedException {
        return this.sendMessageAndWaitForReply(destination, KadUDPMessage.createLookupMessage(localNode, this.getNextCommId(), key));
    }

    /**
     * Bootstrap (connect to a network)
     *
     * @param node Seed node
     * @throws BootstrapFailedException If the process fails.
     */
    public void bootstrap(Node node) throws BootstrapFailedException {
        int attempsLeft = 3;
        boolean success = false;

        do {
            KadUDPMessage helloMsg;

            try {
                helloMsg = this.sendHello(node);
            } catch (IOException e) {
                e.printStackTrace();
                attempsLeft--;
                continue;
            } catch (InterruptedException e) {
                throw new BootstrapFailedException("The thread was interrupted while waiting for response.");
            }

            /* Received response, insert the bootstrap node into the routing table */
            this.routingTable.insert(node, helloMsg.getTimestamp());
            success = true;
        } while (attempsLeft > 0 && !success);

        if (!success) {
            throw new BootstrapFailedException("Could not connect to the seed node.");
        }

        /* Perform lookup of the nodeID to get more nodes close to us */
        try {
            this.nodeLookup(localNode.getIdentifier());
        } catch (TimeoutException e) {
            e.printStackTrace();
            throw new BootstrapFailedException("Node lookup operation timed out.");
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new BootstrapFailedException("The thread was interrupted while waiting for response.");
        }

        /* Refresh routing table buckets */
        this.refreshBuckets();
    }

    /**
     * Find the K closest nodes to the specified kad_key in the entire peer-to-peer network,
     * updating the routing table in the process.
     *
     * @param lookupKey The kad_key we are looking for
     * @return The K closest nodes to the kad_key.
     * @throws TimeoutException
     * @throws InterruptedException
     */
    public List<Node> nodeLookup(KadKey lookupKey) throws TimeoutException, InterruptedException {
        NodeLookupOperation operation = new NodeLookupOperation(this, lookupKey);
        operation.execute();
        return operation.waitForResult(KademliaConfiguration.OPERATION_TIMEOUT);
    }

    /**
     * Start refreshing kademlia buckets
     */
    public void refreshBuckets() {
        for (int i = 1; i < this.routingTable.getBuckets().length; i++) {
            KadKey myKey = this.localNode.getIdentifier().generateNodeIdByDistance(i);

            NodeLookupOperation operation = new NodeLookupOperation(this, myKey);
            operation.execute(); // Execute the operation, we do not care about the result
        }
    }

    /**
     * Publish the DHT to other peers.
     */
    public void publishDHT() {
        // Publishes DHT to other peers
        synchronized (this) {
            if (this.publishing) {
                return;
            }
            this.publishing = true;
        }

        int total = storageService.countStoredBlocks();

        for (int i = 0; i < total; i = i + KademliaConfiguration.CONCURRENCY) {
            List<KadKey> keys = storageService.listStoredKeys(i, KademliaConfiguration.CONCURRENCY);
            for (KadKey key : keys) {
                try {
                    this.publishKey(key);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        this.publishing = false;
    }

    /**
     * Publishes a kad_key to all replicas, making them know that we have that kad_key stored.
     *
     * @param key The kad_key.
     * @throws KademliaOperationException
     * @throws InterruptedException
     */
    public void publishKey(KadKey key) throws KademliaOperationException, InterruptedException {
        List<Node> closestNodes;

        try {
            closestNodes = this.nodeLookup(key);
        } catch (TimeoutException e) {
            throw new KademliaOperationException("Node lookup timed out. Cannot find closest nodes to kad_key.");
        }

        List<Node> replicaNodes = new ArrayList<>();
        for (int i = 0; i < closestNodes.size() && i < KademliaConfiguration.REPLICATION; i++) {
            replicaNodes.add(closestNodes.get(i));
        }

        if (replicaNodes.isEmpty()) {
            throw new KademliaOperationException("FATAL ERROR: There are not on-line nodes in the network. Make sure the configuration is correct.");
        }

        for (Node node : replicaNodes) {
            if (!node.equals(this.localNode)) {
                try {
                    // Send announce message to all the replicas
                    this.sendMessage(node, KadUDPMessage.createAnnounceMessage(this.localNode, 0, key));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Removes unnecessary keys from storage.
     */
    public void purgeStorage() {
        // Purges storage
        synchronized (this) {
            if (this.doingPurge) {
                return;
            }
            this.doingPurge = true;
        }

        File[] files = StoragePaths.getUploadTemporalPath().toFile().listFiles();
        if (files == null) {
            files = new File[0];
        }
        for (File f : files) {
            if (f.isFile()) {
                if (f.getName().endsWith(".tmp")) {
                    VideoUploadStatus st = VideoUploadStatus.findByToken(f.getName().substring(0, f.getName().length() - 4));
                    if (st == null) {
                        f.delete();
                    } else {
                        if (System.currentTimeMillis() - st.timestamp > (6 * 60 * 60 * 1000)) {
                            f.delete();
                        }
                    }
                } else {
                    VideoUploadStatus st = VideoUploadStatus.findByToken(f.getName());
                    if (st == null) {
                        f.delete();
                    } else {
                        if (System.currentTimeMillis() - st.timestamp > (6 * 60 * 60 * 1000)) {
                            f.delete();
                        }
                    }
                }
            }
        }

        int total = storageService.countStoredBlocks();

        for (int i = 0; i < total; i = i + KademliaConfiguration.CONCURRENCY) {
            List<KadKey> keys = storageService.listStoredKeys(i, KademliaConfiguration.CONCURRENCY);
            for (KadKey key : keys) {
                try {
                    this.deleteKeyIfNotAssigned(key);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        this.doingPurge = false;
    }

    /**
     * Deletes a kad_key if it is not assigned to us.
     *
     * @param key The kad_key.
     * @throws KademliaOperationException
     * @throws InterruptedException
     */
    public void deleteKeyIfNotAssigned(KadKey key) throws KademliaOperationException, InterruptedException {
        List<Node> closestNodes;

        try {
            closestNodes = this.nodeLookup(key);
        } catch (TimeoutException e) {
            throw new KademliaOperationException("Node lookup timed out. Cannot find closest nodes to kad_key.");
        }

        List<Node> replicaNodes = new ArrayList<>();
        for (int i = 0; i < closestNodes.size() && i < KademliaConfiguration.REPLICATION; i++) {
            replicaNodes.add(closestNodes.get(i));
        }

        if (replicaNodes.isEmpty()) {
            throw new KademliaOperationException("FATAL ERROR: There are not on-line nodes in the network. Make sure the configuration is correct.");
        }

        for (Node node : replicaNodes) {
            if (node.equals(this.localNode)) {
                return; // Assigned to us, do not delete
            }
        }

        // Delete the block to get free space.
        storageService.deleteBlockLocal(key);
    }

    /**
     * Stores a block into the distributed hash table.
     *
     * @param key     The block kad_key
     * @param content The content of the block.
     * @throws KademliaOperationException
     * @throws InterruptedException
     */
    public void storeBlockInDHT(KadKey key, byte[] content) throws KademliaOperationException, InterruptedException {
        if (s3) {
            try {
                s3StorageService.store(key, content);
            } catch (IOException e) {
                throw new KademliaOperationException(e.getMessage());
            }
            return;
        }

        if (ipfs) {
            try {
                ipfsStorageService.store(key, content);
            } catch (Exception e) {
                throw new KademliaOperationException(e.getMessage());
            }
            return;
        }

        if (btfs) {
            try {
                btfsStorageService.store(key, content);
            } catch (Exception e) {
                throw new KademliaOperationException(e.getMessage());
            }
            return;
        }

        List<Node> closestNodes;

        try {
            closestNodes = this.nodeLookup(key);
        } catch (TimeoutException e) {
            throw new KademliaOperationException("Node lookup timed out. Cannot find closest nodes to kad_key.");
        }

        List<Node> replicaNodes = new ArrayList<>();
        for (int i = 0; i < closestNodes.size() && i < KademliaConfiguration.REPLICATION; i++) {
            replicaNodes.add(closestNodes.get(i));
        }

        if (replicaNodes.isEmpty()) {
            throw new KademliaOperationException("FATAL ERROR: There are not on-line nodes in the network. Make sure the configuration is correct.");
        }

        List<ContentStorageOperation> storageOps = new ArrayList<>();

        // Store the value in all the replicas
        for (Node n : replicaNodes) {
            ContentStorageOperation op = new ContentStorageOperation(this, n, key, content);
            storageOps.add(op);
            op.start();
        }

        for (ContentStorageOperation op : storageOps) {
            op.join();
        }
    }

    /**
     * Stores a video block in DHT.
     */
    public KadKey storeVideoBlock(KadKey videokey, byte[] content, long b) {
        KadKey key = KadKey.getKeyForBlock(videokey, content, b);
        boolean success = false;
        do {
            try {
                this.storeBlockInDHT(key, content);
                success = true;
            } catch (KademliaOperationException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                break;
            }
        } while (!success);

        return key;
    }

    /**
     * Finds and reads a block from the DHT.
     *
     * @param key The block kad_key.
     * @return The block content.
     * @throws KademliaOperationException
     * @throws InterruptedException
     * @throws BlockNotFoundException
     */
    public byte[] readBlockFromDHT(KadKey key) throws KademliaOperationException, InterruptedException, BlockNotFoundException {
        // First, we check if the block is in the cache

        byte[] cached = cacheService.getBlockIfCached(key);

        if (cached != null) {
            return cached;
        }

        if (s3) {
            byte[] blockData;
            try {
                blockData = s3StorageService.read(key);
            } catch (IOException e) {
                throw new KademliaOperationException(e.getMessage());
            }

            try {
                this.cacheService.storeInCache(key, blockData);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return blockData;
        }

        if (ipfs) {
            byte[] blockData;
            try {
                blockData = ipfsStorageService.read(key);
            } catch (IOException e) {
                throw new KademliaOperationException(e.getMessage());
            }

            try {
                this.cacheService.storeInCache(key, blockData);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return blockData;
        }


        if (btfs) {
            byte[] blockData;
            try {
                blockData = btfsStorageService.read(key);
            } catch (IOException e) {
                throw new KademliaOperationException(e.getMessage());
            }

            try {
                this.cacheService.storeInCache(key, blockData);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return blockData;
        }

        // Kademlia

        // Check if we have the block stored locally

        if (storageService.hasBlockLocal(key)) {
            try {
                return storageService.getBlockLocal(key);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Not in cache, look up for the replicas.

        List<Node> closestNodes;

        try {
            closestNodes = this.nodeLookup(key);
        } catch (TimeoutException e) {
            throw new KademliaOperationException("Node lookup timed out. Cannot find closest nodes to kad_key.");
        }

        List<Node> replicaNodes = new ArrayList<>();
        for (int i = 0; i < closestNodes.size() && i < KademliaConfiguration.REPLICATION; i++) {
            replicaNodes.add(closestNodes.get(i));
        }

        if (replicaNodes.isEmpty()) {
            throw new KademliaOperationException("FATAL ERROR: There are not on-line nodes in the network. Make sure the configuration is correct.");
        }

        Collections.shuffle(replicaNodes); // Shuffle, choose a random replica

        for (Node n : replicaNodes) {
            Socket s;

            try {
                s = n.openConnection();
            } catch (Exception ex) {
                System.out.println("[WARNING] Could not connect to node " + n.toString() + " / Reason: " + ex.getMessage());
                continue;
            }

            try {
                DataInputStream input = new DataInputStream(s.getInputStream());
                DataOutputStream output = new DataOutputStream(s.getOutputStream());

                // Receive IV
                byte[] iv = new byte[16];
                input.readFully(iv);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

                // Send READ
                KadTCPMessage readMsg = KadTCPMessage.createReadMessage(key);
                output.write(readMsg.serializeAndEncrypt(ivParameterSpec));

                // Receive STORE or ERROR
                KadTCPMessage storeMsg = new KadTCPMessage();
                storeMsg.readFromEncryptedStream(input, ivParameterSpec);

                if (storeMsg.isStore() && storeMsg.getRequestKey().equals(key)) {
                    try {
                        s.close(); // Close connection
                    } catch (Exception ex) {
                    }
                    // Store in cache
                    this.cacheService.storeInCache(key, storeMsg.getContent());
                    // Return the block
                    return storeMsg.getContent();
                } else {
                    System.out.println("[WARNING] Block " + key.toString() + " not found in replica  " + n.toString());
                    try {
                        s.close(); // Close connection
                    } catch (Exception ex) {
                    }
                }
            } catch (Exception ex) {
                try {
                    s.close();
                } catch (Exception e) {
                }
            }
        }

        // All failed, block not found

        throw new BlockNotFoundException();
    }

    /**
     * Gets a block as a video index.
     *
     * @param key      The block kad_key
     * @param complete True for complete read, false for min read.
     * @return The video index.
     * @throws InterruptedException
     * @throws KademliaOperationException
     * @throws BlockNotFoundException
     * @throws IOException
     */
    public VideoIndex getVideoIndex(KadKey key, boolean complete) throws InterruptedException, KademliaOperationException, BlockNotFoundException, IOException {
        byte[] block = this.readBlockFromDHT(key);
        ByteArrayInputStream bin = new ByteArrayInputStream(block, 0, block.length);
        DataInputStream din = new DataInputStream(bin);
        VideoIndex vi = new VideoIndex();
        if (complete) {
            vi.readComplete(din);
        } else {
            vi.readMin(din);
        }
        return vi;
    }

    private void deleteBlockLocal(KadKey key) {
        if (s3) {
            s3StorageService.deleteBlockLocal(key);
        } else if (ipfs) {
            ipfsStorageService.deleteBlockLocal(key);
        } else if (btfs) {
            btfsStorageService.deleteBlockLocal(key);
        } else {
            storageService.deleteBlockLocal(key);
        }
    }

    /**
     * Erases video block from local storage.
     * @param key The block kad_key
     * @param videoIndex The video index information
     */
    public void eraseVideoFromStorage(KadKey key, VideoIndex videoIndex) {
        this.deleteBlockLocal(key);
        if (!videoIndex.getPreviewBlock().equals(KadKey.zero())) {
            this.deleteBlockLocal(videoIndex.getPreviewBlock());
        }
        if (!videoIndex.getSchemaBlock().equals(KadKey.zero())) {
            this.deleteBlockLocal(videoIndex.getSchemaBlock());
        }
        for (VideoResolutionIndex vri : videoIndex.getResolutions()) {
            for (KadKey k : vri.getWebmBlocks()) {
                this.deleteBlockLocal(k);
            }
            for (KadKey k : vri.getMp4Blocks()) {
                this.deleteBlockLocal(k);
            }
            for (List<KadKey> lk : vri.getHlsBlocks().values()) {
                if (lk != null) {
                    for (KadKey k : lk) {
                        this.deleteBlockLocal(k);
                    }
                }
            }
        }
    }

    public void shutdown() {
        ended = true;
        try {
            serverTCP.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        serverUDP.close();
    }
}
