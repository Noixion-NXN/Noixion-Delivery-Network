package utils;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Video token manager.
 * Generates one-use tokens for uploading or watching videos.
 */
public class VideoTokenManager {
    public static final int TOKEN_LENGTH_BYTES = 32;

    private static VideoTokenManager instance = null;

    /**
     * @return The Singleton instance of VideoTokenManager.
     */
    public static synchronized VideoTokenManager getInstance() {
        if (instance == null) {
            instance = new VideoTokenManager();
        }
        return instance;
    }

    private final Map<String, VideoToken> tokenMap;

    public VideoTokenManager() {
        tokenMap = new TreeMap<>();
    }

    /**
     * Gnerates a temporal token for watching a video.
     *
     * @param videoId The video identifier.
     * @return The generated token.
     */
    public synchronized String generateTokenForWatching(String videoId) {
        String token;
        do {
            token = this.generateToken();
        } while (tokenMap.containsKey(token));
        tokenMap.put(token, new VideoToken(token, videoId));
        return token;
    }

    /**
     * Gnerates a temporal token for uploading a video.
     *
     * @param videoId The video identifier.
     * @return The generated token.
     */
    public synchronized String generateTokenForUploading(String videoId) {
        String token;
        do {
            token = this.generateToken();
        } while (tokenMap.containsKey(token));
        tokenMap.put(token, new VideoToken(token, videoId, true));
        return token;
    }

    private String generateToken() {
        this.checkTokens();
        SecureRandom random = new SecureRandom();
        byte[] token = new byte[TOKEN_LENGTH_BYTES];
        random.nextBytes(token);
        BigInteger bigInt = new BigInteger(1, token);
        return bigInt.toString(16);
    }

    /**
     * Uses a token.
     *
     * @param token The token to use.
     * @return The video identifier or null if the token does not exists.
     */
    public synchronized String useToken(String token) {
        if (tokenMap.containsKey(token)) {
            VideoToken videoToken = tokenMap.get(token);
            if (!videoToken.expired()) {
                if (videoToken.isUploadToken()) {
                    // Upload tokens are one-use
                    tokenMap.remove(token);
                }
                return videoToken.getVideoId();
            } else {
                tokenMap.remove(token);
                return null;
            }
        } else {
            return null;
        }
    }

    private void checkTokens() {
        // Remove tokens that have expired
        List<String> toRemove = new ArrayList<>();
        for (VideoToken token : tokenMap.values()) {
            if (token.expired()) {
                toRemove.add(token.getToken());
            }
        }
        for (String token : toRemove) {
            tokenMap.remove(token);
        }
    }
}
