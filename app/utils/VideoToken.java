package utils;

/**
 * Represents a temporally token for uploading or watching a video.
 */
public class VideoToken {
    public static final int TOKEN_EXPIRATION_TIME = 60 * 1000; // 1 minute

    private final String token;
    private final String videoId;
    private final long timestamp;
    private final boolean uploadToken;

    public VideoToken(String token, String videoId) {
        this(token, videoId, false);
    }

    public VideoToken(String token, String videoId, boolean isUploadToken) {
        this.token = token;
        this.videoId = videoId;
        this.timestamp = System.currentTimeMillis();
        this.uploadToken = isUploadToken;
    }

    /**
     * Check if the token has expired.
     *
     * @return The true value if the token has expired, false if it is still valid.
     */
    public boolean expired() {
        return (Math.abs(System.currentTimeMillis() - this.getTimestamp()) > TOKEN_EXPIRATION_TIME);
    }

    /**
     * @return The token.
     */
    public String getToken() {
        return token;
    }

    /**
     * @return The associated video identifier.
     */
    public String getVideoId() {
        return videoId;
    }

    /**
     * @return The timestamp (when it was created).
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return true if it is an upload token, false if it is a watch token.
     */
    public boolean isUploadToken() {
        return uploadToken;
    }
}
