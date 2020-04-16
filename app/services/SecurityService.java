package services;

import org.apache.commons.codec.binary.Base64;
import utils.StorageConfiguration;
import utils.StoragePaths;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

@Singleton
public class SecurityService {
    public static final String ASYMMETRIC_CIPHER_ALGORITHM = "RSA";
    public static final String HASHING_ALGORITHM = "SHA-256";

    /**
     /**
     * @return The Noixion's public kad_key path.
     */
    public static Path getPublicKeyPath() {
        return new File("conf/noixion.public.key").toPath();
    }

    private final PublicKey publicKey;

    public SecurityService() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        StorageConfiguration.load();
        File publicKeyFile = getPublicKeyPath().toFile();
        this.publicKey = KeyFactory
                .getInstance(ASYMMETRIC_CIPHER_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(Base64.decodeBase64(
                        new String(Files.readAllBytes(publicKeyFile.toPath()), "UTF-8"))));
    }

    /**
     * @return The Noixion's public kad_key.
     */
    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    /**
     * Validates a public kad_key signature.
     *
     * @param key       The public kad_key.
     * @param signature The signature.
     * @return The true value if the signature is valid, false otherwise.
     */
    public boolean validatePublicKey(PublicKey key, byte[] signature) {
        try {
            MessageDigest sha = MessageDigest.getInstance(HASHING_ALGORITHM);
            sha.update(key.getEncoded());
            byte[] hash = sha.digest();
            Cipher cipher = Cipher.getInstance(ASYMMETRIC_CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, this.publicKey);
            byte[] decryptedSignarure = cipher.doFinal(signature);
            return Arrays.equals(hash, decryptedSignarure);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Creates a public kad_key object from its base64 representation.
     *
     * @param base64 Base64 representation of the public kad_key.
     * @return A PublicKey instance.
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    public PublicKey buildPublicKey(String base64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance(SecurityService.ASYMMETRIC_CIPHER_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(Base64.decodeBase64(base64)));
    }

    /**
     * Encrypts a video token using the client's public kad_key.
     *
     * @param token           The token to encrypt.
     * @param clientPublicKey The client's public kad_key.
     * @return The encrypted token.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public byte[] encryptVideoToken(String token, PublicKey clientPublicKey)
            throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(SecurityService.ASYMMETRIC_CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, clientPublicKey);
        return cipher.doFinal(token.getBytes("UTF-8"));
    }

    /**
     * Encrypts a string token using the client's public kad_key.
     *
     * @param str             The string to encrypt.
     * @param clientPublicKey The client's public kad_key.
     * @return The encrypted string.
     * @throws NoSuchAlgorithmException  If the VM does not support RSA
     * @throws NoSuchPaddingException    If the VM does not support RSA
     * @throws InvalidKeyException       If the kad_key is invalid
     * @throws IllegalBlockSizeException If the string to encrypt is too long
     * @throws BadPaddingException       If the VM does not support RSA
     */
    public byte[] encryptString(String str, PublicKey clientPublicKey)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance(SecurityService.ASYMMETRIC_CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, clientPublicKey);
        return cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
    }
}
