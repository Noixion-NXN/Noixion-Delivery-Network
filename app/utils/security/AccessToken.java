package utils.security;

/**
 * Access token (one-use token).
 */
class AccessToken {
    static final int TOKEN_LENGTH_BYTES = 32; // 256 bits
    private static final long TOKEN_EXPIRATION_TIME = 60 * 1000; // 1 minute

    private String token;
    private long timestamp;

    AccessToken(String token) {
        this.token = token;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Checks if the token has expired.
     *
     * @return true if the token has expired, false otherwise.
     */
    boolean expired() {
        return (Math.abs(System.currentTimeMillis() - this.timestamp) > TOKEN_EXPIRATION_TIME);
    }

    String getToken() {
        return token;
    }
}
