package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.VideoProcessingStatus;
import play.libs.Json;
import utils.StorageConfiguration;
import utils.videos.processing.VideoProcessingTask;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static controllers.AudioController.getAudioList;

/**
 * Video processing service.
 */
@Singleton
public class VideoProcessingService {

    @Inject
    public DHTService dht;

    private  AmazonProcessingService amazonProcessingService;

    private final Map<Long, VideoProcessingTask> tasks;
    private final ExecutorService executor;

    @Inject
    public VideoProcessingService(AmazonProcessingService amazonProcessingService) {
        StorageConfiguration.load();
        this.tasks = new TreeMap<>();
        Config config = ConfigFactory.load();
        executor = Executors.newFixedThreadPool(config.getInt("storage.threads.limit"));

        this.amazonProcessingService = amazonProcessingService;

        if (!amazonProcessingService.isEnabled()) {
            List<VideoProcessingStatus> pending = VideoProcessingStatus.findPending();
            for (VideoProcessingStatus p : pending) {
                processVideo(p);
            }
        }
    }

    /**
     * Starts processing a video.
     * @param video The video.
     */
    public synchronized void processVideo(VideoProcessingStatus video) {
        if (!tasks.containsKey(video.id)) {
            VideoProcessingTask task = new VideoProcessingTask(StorageConfiguration.FFMPEG_BIN, StorageConfiguration.FFPROBE_BIN, video, this);
            tasks.put(video.id, task);
            this.executor.execute(task);
        }
    }

    /**
     * Finish processing a video.
     * @param video The video.
     */
    public synchronized void finishProcessVideo(VideoProcessingStatus video) {
        tasks.remove(video.id);
    }

    /**
     * Retrieves the video processing status.
     * @param video The video.
     * @return The status.
     */
    public JsonNode checkVideoStatus (VideoProcessingStatus video) {
        if (video.ready) {
            String audios = "";
            if (video.extraAudio){
                audios = getAudioList(video);
            }
            return Json.newObject().put("status", "ready").put("index", video.getIndexChunk())
                    .put("audioList", audios);
        } else if (video.error) {
            return Json.newObject().put("status", "error").put("message", video.errorMessage);
        } else {
            synchronized (this) {
                if (tasks.containsKey(video.id)) {
                    return tasks.get(video.id).getTaskProgressReport();
                } else {
                    return Json.newObject().put("status", "unknown");
                }
            }
        }
    }

    /**
     * Stops processing a video.
     * @param video The video.
     */
    public void stopProcessingVideo(VideoProcessingStatus video) {
        synchronized (this) {
            if (tasks.containsKey(video.id)) {
                tasks.get(video.id).interrupt();
            }
        }
    }


}
