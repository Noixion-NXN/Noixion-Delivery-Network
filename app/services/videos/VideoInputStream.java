package services.videos;

import services.DHTService;
import services.kademlia.KadKey;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Simulates a video file, stored all across The DHT
 */
public class VideoInputStream extends InputStream {

    private DHTService dht;
    private int fixedBlockSize;
    private List<KadKey> fileBlocks;

    private long position;
    private int currentBlockNumber;
    private byte[] currentBlock;
    private int currentBlockPosition;

    private final long length;
    private long bytesReadOfStream;
    private long maxBytesToRead;


    public VideoInputStream(DHTService dht, int fixedBlockSize, List<KadKey> fileBlocks) throws IOException {
        super();
        this.dht = dht;
        this.fixedBlockSize = fixedBlockSize;
        this.fileBlocks = fileBlocks;
        this.position = 0;
        this.currentBlock = new byte[0];
        this.currentBlockPosition = 0;
        this.currentBlockNumber = -1;
        this.length = findLength();
        this.bytesReadOfStream = 0;
        this.maxBytesToRead = Long.MAX_VALUE;
    }

    private void fetchNextBlock() throws IOException {
        this.currentBlockPosition = 0;
        this.currentBlockNumber++;
        if (this.currentBlockNumber >= fileBlocks.size()) {
            this.currentBlock = new byte[0];
        } else {
            try {
                //System.out.println("Fetching block " + fileBlocks.get(this.currentBlockNumber).toString());
                this.currentBlock = dht.readBlockFromDHT(fileBlocks.get(this.currentBlockNumber));
                //System.out.println("Fetched block " + fileBlocks.get(this.currentBlockNumber).toString() + " (" + this.currentBlock.length + " bytes)");
            } catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    @Override
    public int available() throws IOException {
        return (int)length;
    }



    @Override
    public int read() throws IOException {
        //System.out.println("READ (" + currentBlockPosition + "/" + currentBlock.length + ")");
        if (this.bytesReadOfStream > this.maxBytesToRead) {
            return -1; // Forced end of stream
        } else if (currentBlockPosition < currentBlock.length) {
            this.bytesReadOfStream++;
            int b = currentBlock[currentBlockPosition++];
            return b & 0xFF;
        } else if (currentBlockNumber < fileBlocks.size() - 1) {
            this.fetchNextBlock();
            return read();
        } else {
            //System.out.println("Reached end of stream");
            return -1; // End of stream
        }
    }

    @Override
    public long skip(long n) throws IOException {
        //System.out.println("SKIP called");
        long remainingSkip = n;

        for (int i = currentBlockPosition; i < currentBlock.length && remainingSkip > 0; i++) {
            remainingSkip--;
        }

        long completedBlocksToSkip = remainingSkip / fixedBlockSize;
        long partialBlockSkip = remainingSkip % fixedBlockSize;

        currentBlockNumber += completedBlocksToSkip;

        this.fetchNextBlock();

        this.currentBlockPosition += partialBlockSkip;

        return n;
    }

    public long length() {
        return this.length;
    }

    private long findLength() throws IOException {
        if (fileBlocks.isEmpty()) {
            return 0;
        } else {
            KadKey last = fileBlocks.get(fileBlocks.size() - 1);
            try {
                return ((fileBlocks.size() - 1) * fixedBlockSize) + dht.readBlockFromDHT(last).length;
            } catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    public long getBytesReadOfStream() {
        return bytesReadOfStream;
    }

    public long getMaxBytesToRead() {
        return maxBytesToRead;
    }

    public void setMaxBytesToRead(long maxBytesToRead) {
        this.maxBytesToRead = maxBytesToRead;
    }
}
