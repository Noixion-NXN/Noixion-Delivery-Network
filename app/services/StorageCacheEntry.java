package services;

import services.kademlia.KadKey;

import java.nio.file.Path;

/**
 * It is an entry in the storage cache.
 */
public class StorageCacheEntry implements Comparable<StorageCacheEntry> {
    private final KadKey key;
    private final Path filePath;
    private long lastUsage;

    public StorageCacheEntry(KadKey key, Path filePath) {
        this.key = key;
        this.filePath = filePath;
        this.lastUsage = System.currentTimeMillis();
    }

    public void updateLastUsageNow() {
        this.lastUsage = System.currentTimeMillis();
    }

    @Override
    public int compareTo(StorageCacheEntry o) {
        return Long.compare(this.lastUsage, o.lastUsage);
    }

    public KadKey getKey() {
        return key;
    }

    public Path getFilePath() {
        return filePath;
    }

    public long getLastUsage() {
        return lastUsage;
    }

    public void setLastUsage(long lastUsage) {
        this.lastUsage = lastUsage;
    }
}
