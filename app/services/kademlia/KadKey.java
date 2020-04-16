package services.kademlia;

import com.google.common.primitives.Longs;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.Hash;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.BitSet;

/**
 * 256 bit kad_key
 */
public class KadKey implements Comparable<KadKey> {
    private byte[] bytes;

    public KadKey(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes, 32);
    }

    /**
     * Creates a kad_key form hexadecimal.
     * @param hex Hexadecimal string.
     * @return The kad_key.
     */
    public static KadKey fromHex(String hex) {
        return new KadKey(Hex.decode(hex));
    }

    public static KadKey zero() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 0);
        return new KadKey(bytes);
    }

    /**
     * Creates a random kad_key.
     * @return A random kad_key.
     */
    public static KadKey random() {
        byte[] bytes = new byte[32];
        (new SecureRandom()).nextBytes(bytes);
        return new KadKey(bytes);
    }

    /**
     * Creates a kad_key for a public kad_key
     * @param key The public kad_key.
     * @return The kad_key.
     */
    public static KadKey fromPublicKey(ECKey key) {
        return new KadKey(Hash.sha3(key.getPubKey()));
    }

    public static KadKey forNode(String address, int port) {
        return new KadKey(Hash.sha3((address + ":" + port).getBytes()));
    }

    /**
     * Gnerates a kad_key for a block.
     * @param videoKey The video kad_key.
     * @param content The block content
     * @param partNum The part number.
     * @return The kad_key.
     */
    public static KadKey getKeyForBlock(KadKey videoKey, byte[] content, long partNum) {
        byte[] numAsBytes = Longs.toByteArray(partNum);
        byte[] bytes = new byte[32];
        byte[] contentHash = Hash.sha3(content);
        for (int i = 0; i < 8; i++) {
            bytes[i] = contentHash[i];
        }
        for (int i = 0; i < 8; i++) {
            bytes[8 + i] = numAsBytes[i];
        }
        for (int i = 0; i < 16; i++) {
            bytes[16 + i] = videoKey.getBytes()[i];
        }
        return new KadKey(bytes);
    }

    /**
     * Checks if the kad_key correspond to a valid index kad_key.
     * @return
     */
    public boolean isValidIndexKey() {
        byte[] numAsBytes = Longs.toByteArray(0);
        for (int i = 0; i < 8; i++) {
            if (bytes[8 + i] != numAsBytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates a kad_key. The content hash 8 first bytes must be = to the 8 first bytes of the kad_key.
     * @param content The block content.
     * @return True if is valid.
     */
    public boolean validateForContent(byte[] content) {
        byte[] contentHash = Hash.sha3(content);
        for (int i = 0; i < 8; i++) {
            if (bytes[i] != contentHash[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return The kad_key bytes.
     */
    public byte[] getBytes() {
        return bytes;
    }

    public BigInteger getBigInteger() {
        return new BigInteger(1, this.bytes);
    }

    /**
     * Counts the number of left 0s.
     * @return The number of left 0s.
     */
    public int getFirstSetBitIndex()
    {
        int prefixLength = 0;

        for (byte b : this.bytes)
        {
            if (b == 0)
            {
                prefixLength += 8;
            }
            else
            {
                /* If the byte is not 0, we need to count how many MSBs are 0 */
                int count = 0;
                for (int i = 7; i >= 0; i--)
                {
                    boolean a = (b & (1 << i)) == 0;
                    if (a)
                    {
                        count++;
                    }
                    else
                    {
                        break;   // Reset the count if we encounter a non-zero number
                    }
                }

                /* Add the count of MSB 0s to the prefix length */
                prefixLength += count;

                /* Break here since we've now covered the MSB 0s */
                break;
            }
        }
        return prefixLength;
    }

    /**
     * Computes XOR.
     * @param nid Other kad_key.
     * @return The result.
     */
    public KadKey xor(KadKey nid)
    {
        byte[] result = new byte[32];
        byte[] nidBytes = nid.getBytes();

        for (int i = 0; i < 32; i++)
        {
            result[i] = (byte) (this.bytes[i] ^ nidBytes[i]);
        }

        return new KadKey(result);
    }

    /**
     * Computes the XOR distance.
     * @param other Other kad_key.
     * @return The result.
     */
    public int distance(KadKey other) {
        return 256 - this.xor(other).getFirstSetBitIndex();
    }

    /**
     * Generates a new kad_key that has a specific distance from this kad_key.
     * @param distance The required distance.
     * @return The new kad_key.
     */
    public KadKey generateNodeIdByDistance(int distance)
    {
        byte[] result = new byte[32];

        /* Since distance = ID_LENGTH - prefixLength, we need to fill that amount with 0's */
        int numByteZeroes = (256 - distance) / 8;
        int numBitZeroes = 8 - (distance % 8);

        /* Filling byte zeroes */
        for (int i = 0; i < numByteZeroes; i++)
        {
            result[i] = 0;
        }

        /* Filling bit zeroes */
        BitSet bits = new BitSet(8);
        bits.set(0, 8);

        for (int i = 0; i < numBitZeroes; i++)
        {
            /* Shift 1 zero into the start of the value */
            bits.clear(i);
        }
        bits.flip(0, 8);        // Flip the bits since they're in reverse order
        result[numByteZeroes] = (byte) bits.toByteArray()[0];

        /* Set the remaining bytes to Maximum value */
        for (int i = numByteZeroes + 1; i < result.length; i++)
        {
            result[i] = Byte.MAX_VALUE;
        }

        return this.xor(new KadKey(result));
    }

    @Override
    public String toString()
    {
        return Hex.toHexString(this.bytes).toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof KadKey) {
            return Arrays.equals(this.bytes, ((KadKey)o).bytes);
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(KadKey o) {
        return this.getBigInteger().compareTo(o.getBigInteger());
    }
}
