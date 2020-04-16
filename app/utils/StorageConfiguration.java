package utils;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import utils.videos.processing.VideoSpecification;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Noixion storage configuration.
 */
public class StorageConfiguration {

    public static Path STORAGE_PATH = null;
    public static Path FFMPEG_BIN = null;
    public static Path FFPROBE_BIN = null;

    public static Path UPLOAD_TEMP_PATH = null;
    public static Path VIDEO_STORAGE_PATH = null;

    public static String MP4_SPEED = "ultrafast";

    public static String REGISTRATION_KEY = null;

    private static boolean loaded = false;

    private static final List<VideoSpecification> supportedSpecifications = new ArrayList<>();
    private static VideoSpecification defaultSpec = null;
    public static int FRAGMENT_SIZE = 3;

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        Config config = ConfigFactory.load();
        if (config.hasPath("storage.path") && config.getString("storage.path").length() > 0) {
            STORAGE_PATH = new File(config.getString("storage.path")).toPath();
        } else {
            STORAGE_PATH = new File(System.getProperty("user.home")).toPath();
        }

        FFMPEG_BIN = (new File(config.getString("ffmpeg.ffmpeg.path"))).toPath().toAbsolutePath();
        FFPROBE_BIN = (new File(config.getString("ffmpeg.ffprobe.path"))).toPath().toAbsolutePath();

        if (config.hasPath("storage.mp4.preset")) {
            MP4_SPEED = config.getString("storage.mp4.preset");
        }

        STORAGE_PATH.toFile().mkdirs();

        UPLOAD_TEMP_PATH = STORAGE_PATH.resolve("temp");
        UPLOAD_TEMP_PATH.toFile().mkdirs();
        VIDEO_STORAGE_PATH = STORAGE_PATH.resolve("videos");
        VIDEO_STORAGE_PATH.toFile().mkdirs();

        REGISTRATION_KEY = config.getString("registration.kad_key");

        String resolutions = config.getString("storage.resolutions");

        Set<String> supResolutions = new TreeSet<>();

        String[] resSpl = resolutions.split(",");
        for (String f : resSpl) {
            supResolutions.add(f.toLowerCase().trim());
        }

        supportedSpecifications.clear();

        for (String resName : supResolutions) {
            supportedSpecifications.add(new VideoSpecification(resName));
            System.out.println((new VideoSpecification(resName)).toString());
        }

        for (VideoSpecification spec : supportedSpecifications) {
            if (defaultSpec == null || defaultSpec.getSize() > spec.getSize()) {
                defaultSpec = spec;
            }
        }

        loaded = true;
    }

    public static VideoSpecification[] getSpecificationsForVideo(int width, int height) {
        List<VideoSpecification> specs = new ArrayList<>();
        long size = width * height;
        for (VideoSpecification spec : supportedSpecifications) {
            if (spec == defaultSpec || spec.getSize() <= size) {
                specs.add(spec);
            }
        }
        VideoSpecification[] res = new VideoSpecification[specs.size()];
        res = specs.toArray(res);
        return res;
    }

    public static String getResolutionsList() {
        String specs = "";
        for (VideoSpecification spec : supportedSpecifications) {
            if (specs.length() > 0) {
                specs = specs + ",";
            }
            specs = specs + spec.getResolutionName();
        }
        return specs;
    }

    public static VideoSpecification[] getBestSpecification(VideoSpecification[] specs) {
        VideoSpecification best = null;

        if (specs.length == 0) {
            return specs;
        }

        for (VideoSpecification spec : specs) {
            if (best == null || best.getSize() < spec.getSize()) {
                best = spec;
            }
        }

        VideoSpecification[] result = new VideoSpecification[1];
        result[0] = best;
        return result;
    }
}
