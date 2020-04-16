package utils.security;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Access token manager. Generates one-use token for accessing the services.
 */
public class AccessTokenManager {
    private final Map<String, AccessToken> tokenMap;

    private static AccessTokenManager instance = null;

    /**
     * @return The Singleton instance of AccessTokenManager
     */
    public static synchronized AccessTokenManager getInstance() {
        if (instance == null) {
            instance = new AccessTokenManager();
        }
        return instance;
    }

    private AccessTokenManager() {
        tokenMap = new TreeMap<>();
    }

    /**
     * Generates an one-use access token.
     *
     * @return The generated token.
     */
    public synchronized String generateAccessToken() {
        String token;
        do {
            token = this.generateToken();
        } while (tokenMap.containsKey(token));
        tokenMap.put(token, new AccessToken(token));
        return token;
    }

    private String generateToken() {
        this.checkTokens();
        SecureRandom random = new SecureRandom();
        byte[] token = new byte[AccessToken.TOKEN_LENGTH_BYTES];
        random.nextBytes(token);
        BigInteger bigInt = new BigInteger(1, token);
        return bigInt.toString(16);
    }

    /**
     * Uses a token.
     *
     * @param token The token to use.
     * @return true if sucess, false if failure.
     */
    public synchronized boolean useToken(String token) {
        if (tokenMap.containsKey(token)) {
            AccessToken accessToken = tokenMap.get(token);
            if (!accessToken.expired()) {
                // One-use token, remove it
                tokenMap.remove(token);
                return true;
            } else {
                tokenMap.remove(token);
                return false;
            }
        } else {
            return false;
        }
    }

    private void checkTokens() {
        // Remove tokens that have expired
        List<String> toRemove = new ArrayList<>();
        for (AccessToken token : tokenMap.values()) {
            if (token.expired()) {
                toRemove.add(token.getToken());
            }
        }
        for (String token : toRemove) {
            tokenMap.remove(token);
        }
    }
}
