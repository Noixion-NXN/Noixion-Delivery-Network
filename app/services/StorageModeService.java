package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StorageModeService {

    private boolean s3;
    private boolean kademlia;
    private boolean ipfs;
    private boolean btfs;

    @Inject
    public StorageModeService(ApplicationLifecycle applicationLifecycle) {
        Config config = ConfigFactory.load();

        String mode = config.getString("storage.mode");

        if (mode.equalsIgnoreCase("kademlia")) {
            s3 = false;
            kademlia = true;
            ipfs = false;
            btfs = false;
            System.out.println("[STORAGE] Using Kademlia DHT as storage");
        } else if (mode.equalsIgnoreCase("s3")) {
            s3 = true;
            kademlia = false;
            ipfs = false;
            btfs = false;
            System.out.println("[STORAGE] Using AWS S3 bucket as storage");
        } else if (mode.equalsIgnoreCase("ipfs")) {
            s3 = false;
            kademlia = false;
            ipfs = true;
            btfs = false;
            System.out.println("[STORAGE] Using IPFS as storage");
        } else if (mode.equalsIgnoreCase("btfs")) {
            s3 = false;
            kademlia = false;
            ipfs = false;
            btfs = true;
            System.out.println("[STORAGE] Using BTFS as storage");
        } else {
            throw new RuntimeException("Error: Unrecognized storage mode: " + mode);
        }
    }

    public boolean isS3() {
        return s3;
    }

    public boolean isIPFS() {
        return ipfs;
    }

    public boolean isBTFS() {
        return btfs;
    }

    public boolean isKademlia() {
        return kademlia;
    }
}
