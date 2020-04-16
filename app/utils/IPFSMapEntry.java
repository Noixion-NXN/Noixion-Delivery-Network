package utils;

import org.bouncycastle.util.encoders.Hex;
import play.db.Database;
import services.kademlia.KadKey;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * IPFS mapping entry.
 */
public class IPFSMapEntry {

    private static final String CIPHER_ALGORITHM = "AES/CTR/NoPadding";

    private KadKey hash;
    private String file;
    private String cryptoKey;

    public KadKey getHash() {
        return hash;
    }

    public void setHash(KadKey hash) {
        this.hash = hash;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getCryptoKey() {
        return cryptoKey;
    }

    public void setCryptoKey(String cryptoKey) {
        this.cryptoKey = cryptoKey;
    }

    public void store(Database db) throws SQLException {
        Connection con = db.getConnection();
        PreparedStatement stmt = null;

        try {
            stmt = con.prepareStatement("INSERT INTO ipfs_map(chunk_hash, ipfs_file, crypto_key) VALUES(?, ?, ?)");

            stmt.setString(1, hash.toString());
            stmt.setString(2, file);
            stmt.setString(3, cryptoKey);

            stmt.executeUpdate();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            con.close();
        }
    }

    public void query(Database db, KadKey key) throws SQLException {
        this.hash = key;
        Connection con = db.getConnection();
        PreparedStatement stmt = null;

        try {
            stmt = con.prepareStatement("SELECT chunk_hash, ipfs_file, crypto_key FROM ipfs_map WHERE chunk_hash = ?");

            stmt.setString(1, hash.toString());

            ResultSet rs = stmt.executeQuery();

            rs.beforeFirst();

            while (rs.next()) {
                this.file = rs.getString("ipfs_file");
                this.cryptoKey = rs.getString("crypto_key");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            con.close();
        }
    }

    public void delete(Database db) throws SQLException {
        Connection con = db.getConnection();
        PreparedStatement stmt = null;

        try {
            stmt = con.prepareStatement("DELETE FROM ipfs_map WHERE chunk_hash = ?");

            stmt.setString(1, hash.toString());

            stmt.executeUpdate();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            con.close();
        }
    }

    public byte[] decrypt(byte[] crypted) throws Exception {
        if (this.cryptoKey.startsWith("AES256:")) {
            String[] parts = this.cryptoKey.split(":");
            byte[] keyBytes = Hex.decode(parts[1]);
            byte[] ivBytes = Hex.decode(parts[2]);

            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            SecretKey sc = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, sc, ivParameterSpec);

            return cipher.doFinal(crypted);
        } else {
            throw new IOException("Algorithm not supported.");
        }
    }

    public byte[] encrypt(byte[] raw) throws Exception {
        byte[] iv = new byte[16];
        (new SecureRandom()).nextBytes(iv);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
        SecretKey secretKey = KeyGenerator.getInstance("AES").generateKey();
        String stringKey = Hex.toHexString(secretKey.getEncoded());

        this.cryptoKey = "AES256:" + stringKey + ":" + Hex.toHexString(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);

        return cipher.doFinal(raw);
    }
}
