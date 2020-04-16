package services;

import models.KadBlock;
import services.kademlia.KadKey;
import utils.StorageConfiguration;
import utils.StoragePaths;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local storage service for DHT.
 */
@Singleton
public class StorageService {

    public StorageService() {
        StorageConfiguration.load();
    }

    /**
     * Stores a block in the local filesystem.
     * @param key The block kad_key.
     * @param content The block content.
     * @throws IOException
     */
    public void storeBlockLocal(KadKey key, byte[] content) throws IOException {
        File file = this.getBlockPath(key, true).toFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content);
        fos.close();
        KadBlock.store(key.toString(), content.length);
    }

    /**
     * Checks if a block is stored locally.
     * @param key The block kad_key.
     * @return True if the block is stored locally.
     */
    public boolean hasBlockLocal(KadKey key) {
        return getBlockPath(key).toFile().exists();
    }

    /**
     * Reads a block stored locally.
     * @param key The block kad_key.
     * @return The block content.
     * @throws IOException
     */
    public byte[] getBlockLocal(KadKey key) throws IOException {
        return Files.readAllBytes(this.getBlockPath(key));
    }

    /**
     * Deletes a block stored locally.
     * @param key The block kad_key.
     */
    public void deleteBlockLocal(KadKey key) {
        try {
            getBlockPath(key).toFile().delete();
        } catch (Exception ex) {}
        KadBlock.remove(key.toString());
    }

    private Path getBlockPath(KadKey key, boolean create) {
        String keyHex = key.toString();
        Path videoPath = StoragePaths.getChunkStoragePath().resolve(keyHex.substring(16));
        if (create)
            videoPath.toFile().mkdirs();
        return videoPath.resolve(keyHex.substring(0, 16));
    }

    private Path getBlockPath(KadKey key) {
        return getBlockPath(key, false);
    }

    /**
     * Counts the total number of stored blocks.
     * @return The total number of stored blocks.
     */
    public int countStoredBlocks() {
        return KadBlock.countAll();
    }

    /**
     * Lists the stored blocks, paginated
     * @param offset The offset
     * @param limit The limit
     * @return The list of keys
     */
    public List<KadKey> listStoredKeys(int offset, int limit) {
        List<KadBlock> blocks = KadBlock.getPage(offset, limit);
        List<KadKey> result = new ArrayList<>();
        for (KadBlock b : blocks) {
            result.add(b.getKadKey());
        }
        return result;
    }
}
