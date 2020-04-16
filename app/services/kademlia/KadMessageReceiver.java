package services.kademlia;

public interface KadMessageReceiver {
    void receive(KadUDPMessage message);
    void timeout(int commId);
}
