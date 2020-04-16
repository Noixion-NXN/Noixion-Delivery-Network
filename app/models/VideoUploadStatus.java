package models;

import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.annotation.Index;
import services.AmazonProcessingService;
import utils.StorageConfiguration;
import utils.StoragePaths;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SecureRandom;

/**
 * Stores the status of an uploaded video.
 */
@Entity
public class VideoUploadStatus extends Model {

    public static final int TOKEN_LENGTH_BYTES = 32;

    private static Finder<Long, VideoUploadStatus> find = new Finder<>(VideoUploadStatus.class);

    @Id
    public Long id;

    /**
     * Upload token, 256-bit hexadecimal. Randomly generated.
     */
    @Column(unique = true)
    @Index
    public String token;

    /**
     * Token creation timestamp.
     */
    public long timestamp;

    /**
     * True if the video is 100% uploaded.
     */
    public boolean uploaded;

    /**
     * Quality vid_options set in the upload.
     */
    public VideoProcessingOptions options;

    public VideoUploadStatus(String token, VideoProcessingOptions options) {
        this.token = token;
        this.options = options;
        this.timestamp = System.currentTimeMillis();
        this.uploaded = false;
    }

    private static String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] token = new byte[TOKEN_LENGTH_BYTES];
        random.nextBytes(token);
        BigInteger bigInt = new BigInteger(1, token);
        return bigInt.toString(16);
    }

    public static VideoUploadStatus findByToken(String token) {
        return find.query().where().eq("token", token).findOne();
    }

    public static VideoUploadStatus findById(Long id) {
        return find.byId(id);
    }


    public static VideoUploadStatus create(VideoProcessingOptions options) {
        StorageConfiguration.load();
        String token = "";
        do {
            token = generateToken();
        } while (findByToken(token) != null);
        VideoUploadStatus uploadedVideo = new VideoUploadStatus(token, options);
        uploadedVideo.save();
        return uploadedVideo;
    }

    /**
     * @return The path to the upload temporal file, for Resumable
     */
    public Path getTemporalFile() {
        return StoragePaths.getUploadTemporalPath().resolve(this.token + ".tmp");
    }

    /**
     * @return The path to the complete file.
     */
    public Path getFile() {
        return StoragePaths.getUploadTemporalPath().resolve(this.token);
    }

    /**
     * Marks the upload as finished.
     *
     * @throws IOException
     */
    public void finishUpload(AmazonProcessingService amazonProcessingService) {
        if (amazonProcessingService.isEnabled()) {
            try {
                amazonProcessingService.storeFileAndDestroy(this.token, this.getFile().toFile());
            } catch (IOException e) {
                this.getFile().toFile().delete();
                e.printStackTrace();
            }
        }

        this.uploaded = true;
        this.save();
    }

    /**
     * Deletes the files.
     */
    public void deleteFiles() {
        this.getTemporalFile().toFile().delete();
        this.getFile().toFile().delete();
    }
}
