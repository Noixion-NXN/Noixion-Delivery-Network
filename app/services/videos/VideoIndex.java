package services.videos;

import services.kademlia.KadKey;
import services.kademlia.KademliaConfiguration;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Video index.
 */
public class VideoIndex {

    public static final int FIXED_VIDEO_BLOCK_SIZE = 1024 * 1024; // 1MB

    private int fixedBlockSize;
    private KadKey schemaBlock;
    private KadKey previewBlock;

    private final List<VideoResolutionIndex> resolutions;

    public VideoIndex() {
        this.fixedBlockSize = FIXED_VIDEO_BLOCK_SIZE;
        this.schemaBlock = KadKey.zero();
        this.previewBlock = KadKey.zero();
        this.resolutions = new ArrayList<>();
    }

    /**
     * @return The serialized object.
     */
    public byte[] serialize() {
        int size = 4 + 32 + 32 + 4;
        List<byte[]> serializedRes = new ArrayList<>();
        for (VideoResolutionIndex res : resolutions) {
            byte[] s = res.serialize();
            size += s.length;
            serializedRes.add(s);
        }
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(fixedBlockSize);
        buf.put(schemaBlock.getBytes());
        buf.put(previewBlock.getBytes());
        buf.putInt(resolutions.size());
        for (byte[] b : serializedRes) {
            buf.put(b);
        }
        return buf.array();
    }

    /**
     * Reads the object, just the schema and preview.
     * @param din
     * @throws IOException
     */
    public void readMin(DataInputStream din) throws IOException {
        this.fixedBlockSize = din.readInt();

        if (this.fixedBlockSize <= 0 || this.fixedBlockSize > KademliaConfiguration.MAX_BLOCK_SIZE) {
            throw new IOException("Invalid block size.");
        }

        byte[] schemaBytes = new byte[32];
        din.readFully(schemaBytes);
        this.schemaBlock = new KadKey(schemaBytes);

        byte[] previewBytes = new byte[32];
        din.readFully(previewBytes);
        this.previewBlock = new KadKey(previewBytes);
    }

    /**
     * Reads the object fully.
     * @param din
     * @throws IOException
     */
    public void readComplete(DataInputStream din) throws IOException {
        this.readMin(din);

        int resCount = din.readInt();
        if (resCount < 0 || resCount > 255) {
            throw new IOException("Invalid resolution list size.");
        }

        for (int i = 0; i < resCount; i++) {
            VideoResolutionIndex vi = new VideoResolutionIndex("");
            vi.read(din);
            this.resolutions.add(vi);
        }
    }

    public VideoResolutionIndex findResolution (String name) {
        for (VideoResolutionIndex res : resolutions) {
            if (res.getResolutionName().equals(name)) {
                return res;
            }
        }
        return null;
    }


    public KadKey getSchemaBlock() {
        return schemaBlock;
    }

    public void setSchemaBlock(KadKey schemaBlock) {
        this.schemaBlock = schemaBlock;
    }

    public KadKey getPreviewBlock() {
        return previewBlock;
    }

    public void setPreviewBlock(KadKey previewBlock) {
        this.previewBlock = previewBlock;
    }

    public List<VideoResolutionIndex> getResolutions() {
        return resolutions;
    }

    public int getFixedBlockSize() {
        return fixedBlockSize;
    }

    public void setFixedBlockSize(int fixedBlockSize) {
        this.fixedBlockSize = fixedBlockSize;
    }
}
