package models;

import io.ebean.Finder;
import io.ebean.Model;
import io.ebean.annotation.Index;
import org.apache.commons.io.FileUtils;
import utils.StoragePaths;
import utils.videos.processing.VideoSpecification;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the video processing status.
 */
@Entity
public class VideoProcessingStatus extends Model {

    private static Finder<Long, VideoProcessingStatus> find = new Finder<>(VideoProcessingStatus.class);

    /**
     * Sequence ID.
     */
    @Id
    public Long id;

    /**
     * Video Kademlia kad_key used for selecting the processing server.
     */
    @Column(unique = true)
    @Index
    public String kad_key;

    /**
     * First 128 bytes of the kad_key. Must be unique.
     */
    @Column(unique = true)
    @Index
    public String firstPartKey;

    /**
     * Quality vid_options set in the upload.
     */
    public VideoProcessingOptions vid_options;

    /**
     * True if the video is in progress.
     */
    public boolean inProgress;

    /**
     * Timestamp since the processing status started.
     */
    public long startTimestamp;

    /**
     * Duration of the video.
     */
    public double duration;

    /**
     * True if the video is processed.
     */
    public boolean processed;

    /**
     * True if the video is ready.
     */
    public boolean ready;

    /**
     * True if the processing had an error.
     */
    public boolean error;

    /**
     * Error message
     */
    public String errorMessage;

    private String indexChunk;

    public Boolean extraAudio;

    public static VideoProcessingStatus findById(Long id) {
        return find.byId(id);
    }

    public static VideoProcessingStatus findByKey(String key) {
        return find.query().where().eq("kad_key", key).findOne();
    }

    public static List<VideoProcessingStatus> findPending() {
        try {
            return find.query().where().eq("ready", false).eq("error", false).findList();
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    public VideoProcessingStatus(String kad_key, VideoProcessingOptions vid_options) {
        this.kad_key = kad_key;
        this.firstPartKey = this.kad_key.substring(0, 32);
        this.vid_options = vid_options;
        this.inProgress = false;
        this.ready = false;
        this.error = false;
        this.errorMessage = "";
        this.processed = false;
    }

    public void markStarted() {
        this.inProgress = true;
        this.startTimestamp = System.currentTimeMillis();
        this.save();
    }

    public void markReady() {
        this.ready = true;
        this.save();
    }

    public void markError() {
        this.error = true;
        this.save();
    }


    public Path getVideoProcessingPath() {
        Path p = StoragePaths.getProcessingPath().resolve("" + this.id);
        p.toFile().mkdirs();
        return p;
    }

    public Path getOriginalVideoPath() {
        return this.getVideoProcessingPath().resolve("original");
    }

    public Path getSchemaFile() {
        return this.getVideoProcessingPath().resolve("schema.json");
    }

    public Path getIndexFile() {
        return this.getVideoProcessingPath().resolve("video.index");
    }

    public Path getQualityPath(VideoSpecification sp) {
        Path p = this.getVideoProcessingPath().resolve("" + sp.getResolutionName());
        p.toFile().mkdirs();
        return p;
    }

    public Path getTemporalPath() {
        Path p = this.getVideoProcessingPath().resolve("preview_temp");
        p.toFile().mkdirs();
        return p;
    }

    public Path getPreview() {
        return this.getVideoProcessingPath().resolve("preview.gif");
    }

    public Path getVideoMP4(VideoSpecification sp) {
        return this.getQualityPath(sp).resolve("video.mp4");
    }

    public Path getAudioPath() {
        Path p = StoragePaths.getAudioProcessingPath().resolve(this.id.toString());
        p.toFile().mkdirs();
        return p;
    }

    public Path getVideoWebM(VideoSpecification sp) {
        return this.getQualityPath(sp).resolve("video.webm");
    }

    public Path getPathHLS(VideoSpecification sp) {
        Path p = this.getQualityPath(sp).resolve("hls");
        p.toFile().mkdirs();
        return p;
    }

    public Path getVideoWebMRemuxed(VideoSpecification sp) {
        return this.getQualityPath(sp).resolve("video.raw.webm");
    }

    public Path getManifest(VideoSpecification sp) {
        return this.getQualityPath(sp).resolve("manifest.json");
    }

    public byte[] getWebMFragment(VideoSpecification spec, long offset, int size) throws IOException {
        byte[] data = new byte[size];
        RandomAccessFile ra = new RandomAccessFile(this.getVideoWebMRemuxed(spec).toAbsolutePath().toString(), "r");
        try {
            ra.seek(offset);
            ra.readFully(data);
        } catch (IOException ex) {
            ra.close();
            throw ex;
        }
        ra.close();
        return data;
    }

    public byte[] getHLSFile(VideoSpecification spec, String file) {
        try {
            return Files.readAllBytes(this.getPathHLS(spec).resolve(file));
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }


    public void deleteAllFiles() {
        try {
            FileUtils.deleteDirectory(getVideoProcessingPath().toFile());
        } catch (IOException ex) {
        }
    }

    public void deleteOriginalVideo() {
        this.getOriginalVideoPath().toFile().delete();
    }

    /**
     * Kademlia kad_key for the index chunk.
     */
    public String getIndexChunk() {
        return indexChunk;
    }

    public void setIndexChunk(String indexChunk) {
        this.indexChunk = indexChunk;
    }
}
