package services.videos;

import services.kademlia.KadKey;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Index for a video in a specific resolution
 */
public class VideoResolutionIndex {
    private String resolutionName;
    private final List<KadKey> mp4Blocks;
    private final List<KadKey> webmBlocks;
    private final Map<String, List<KadKey>> hlsBlocks;

    public VideoResolutionIndex(String resolutionName) {
        this.resolutionName = resolutionName;
        this.mp4Blocks = new ArrayList<>();
        this.webmBlocks = new ArrayList<>();
        this.hlsBlocks = new TreeMap<>();
    }

    /**
     * @return The object serialized as byte array.
     */
    public byte[] serialize() {
        int size = 0;

        // Compute size
        byte[] nameBytes = this.resolutionName.getBytes();
        size += 4;
        size += nameBytes.length;

        size += 4;
        size += 32 * mp4Blocks.size();

        size += 4;
        size += 32 * webmBlocks.size();

        size += 4;
        for (String key : hlsBlocks.keySet()) {
            size += 4;
            size += key.getBytes().length;
            size += 4;
            size += 32 * hlsBlocks.get(key).size();
        }


        // Serialize
        ByteBuffer buf = ByteBuffer.allocate(size);

        buf.putInt(nameBytes.length);
        buf.put(nameBytes);

        buf.putInt(mp4Blocks.size());
        for (KadKey key : mp4Blocks) {
            buf.put(key.getBytes());
        }

        buf.putInt(webmBlocks.size());
        for (KadKey key : webmBlocks) {
            buf.put(key.getBytes());
        }

        buf.putInt(hlsBlocks.size());
        for (String key : hlsBlocks.keySet()) {
            buf.putInt(key.getBytes().length);
            buf.put(key.getBytes());
            List<KadKey> blocks = hlsBlocks.get(key);
            buf.putInt(blocks.size());
            for (KadKey kk : blocks) {
                buf.put(kk.getBytes());
            }
        }

        return buf.array();
    }

    /**
     * Reads this object from input stream.
     * @param din Input stream.
     * @throws IOException
     */
    public void read(DataInputStream din) throws IOException {
        int nameSize = din.readInt();
        if (nameSize < 1 || nameSize > 64) {
            throw new IOException("Invalid resolution name");
        }
        byte[] resBytes = new byte[nameSize];
        din.readFully(resBytes);
        this.resolutionName = new String(resBytes);
        int mp4BlockCount = din.readInt();
        for (int i = 0; i < mp4BlockCount; i++) {
            byte[] key = new byte[32];
            din.readFully(key);
            this.mp4Blocks.add(new KadKey(key));
        }
        int webmBlockCount = din.readInt();
        for (int i = 0; i < webmBlockCount; i++) {
            byte[] key = new byte[32];
            din.readFully(key);
            this.webmBlocks.add(new KadKey(key));
        }
        int hlsFileCount;

        try {
            hlsFileCount = din.readInt();
        } catch (IOException ex) {
            return;
        }

        for (int i = 0; i < hlsFileCount; i++) {
            int fileNameSize = din.readInt();
            byte[] bytesFile = new byte[fileNameSize];
            din.readFully(bytesFile);
            String fileName = new String(bytesFile);
            List<KadKey> keys = new ArrayList<>();
            hlsBlocks.put(fileName, keys);

            int totalBlocks = din.readInt();
            for (int j = 0; j < totalBlocks; j++) {
                byte[] key = new byte[32];
                din.readFully(key);
                keys.add(new KadKey(key));
            }
        }
    }

    public String getResolutionName() {
        return resolutionName;
    }

    public void setResolutionName(String resolutionName) {
        this.resolutionName = resolutionName;
    }

    public List<KadKey> getMp4Blocks() {
        return mp4Blocks;
    }

    public List<KadKey> getWebmBlocks() {
        return webmBlocks;
    }

    public Map<String, List<KadKey>> getHlsBlocks() {
        return hlsBlocks;
    }
}
