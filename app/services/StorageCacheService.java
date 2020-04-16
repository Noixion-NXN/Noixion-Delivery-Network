package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import services.kademlia.KadKey;
import utils.StorageConfiguration;
import utils.StoragePaths;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Cache service
 */
@Singleton
public class StorageCacheService {

    private final int cacheSize;

    private long nextCache;

    private final Map<KadKey, StorageCacheEntry> mapEntries;
    private final TreeSet<StorageCacheEntry> cache;

    public StorageCacheService() {
        StorageConfiguration.load();
        this.mapEntries = new TreeMap<>();
        this.cache = new TreeSet<>();
        Config config = ConfigFactory.load();
        cacheSize = config.getInt("storage.cache.size");
        nextCache = 0;
    }

    /**
     * Gets a temporal file for storing a cache block.
     * @return The temporal file path.
     */
    public synchronized Path nextCachePath() {
        nextCache++;
        if (nextCache < 0) {
            nextCache = 0;
        }
        return StoragePaths.getCacheStoragePath().resolve("" + nextCache + ".cache");
    }

    /**
     * Checks if a block is cached.
     * @param key The block kad_key.
     * @return True if the block is in the cache.
     */
    public synchronized boolean hasBlock(KadKey key) {
        return mapEntries.containsKey(key);
    }

    /**
     * Gets a block from the cache.
     * @param key The block kad_key.
     * @return The block content, or null.
     */
    public synchronized byte[] getBlockIfCached(KadKey key) {
        if (mapEntries.containsKey(key)) {
            mapEntries.get(key).updateLastUsageNow();
            try {
                return Files.readAllBytes(mapEntries.get(key).getFilePath());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Stores a block in cache.
     * @param key The block kad_key.
     * @param content the block content.
     * @throws IOException
     */
    public synchronized void storeInCache(KadKey key, byte[] content) throws IOException {
        if (mapEntries.containsKey(key)) {
            StorageCacheEntry entry = mapEntries.get(key);
            cache.remove(entry);
            entry.updateLastUsageNow();
            cache.add(entry);
            return; // Already cached.
        }
        if (cache.size() >= cacheSize) {
            // Cache is filled, must remove the least recently used

            // TODO: DELETE
            mapEntries.remove(cache.pollFirst().getKey());
        }
        StorageCacheEntry entry = new StorageCacheEntry(key, this.nextCachePath());
        cache.add(entry);
        mapEntries.put(key, entry);
        File file = entry.getFilePath().toFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content);
        fos.close();
    }

    /**
     * Clears the cache.
     */
    public synchronized void clearCache() {
        File[] files = StoragePaths.getCacheStoragePath().toFile().listFiles();
        for (File file : files) {
            file.delete();
        }
        this.mapEntries.clear();
        this.cache.clear();
    }
}
