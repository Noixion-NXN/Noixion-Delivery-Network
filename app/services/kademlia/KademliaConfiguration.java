package services.kademlia;

/**
 * Kademlia configuration parameters.
 */
public class KademliaConfiguration {
    public static long RESTORE_INTERVAL = 60 * 1000;
    public static long PURGE_INTERVAL = 4 * 60 * 1000;
    public static long RESPONSE_TIMEOUT = 2000;
    public static long OPERATION_TIMEOUT = 2000;
    public static int CONCURRENCY = 10;
    public static int K = 5;
    public static int RCSIZE = 3;
    public static int STALE = 1;

    public static long MAX_BLOCK_SIZE = 5 * 1024 * 1024;

    public static byte[] NETWORK_PROOF_KEY = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    public static int REPLICATION = 2;
}
