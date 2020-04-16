package utils;

import java.io.File;
import java.nio.file.Path;

public class StoragePaths {
    private static final String BASE_PATH_NAME = ".noixion";
    private static final String FINAL_PATH_NAME = "storage";
    private static final String SECURITY_PATH_NAME = "security";

    public static Path getSecurityPath() {
        Path path = new File(System.getProperty("user.home")).toPath().resolve(BASE_PATH_NAME).resolve(SECURITY_PATH_NAME);
        path.toFile().mkdirs();
        return path;
    }

    public static Path getBasePath() {
        Path path = StorageConfiguration.STORAGE_PATH.resolve(BASE_PATH_NAME).resolve(FINAL_PATH_NAME);
        path.toFile().mkdirs();
        return path;
    }

    public static Path getPath(String name) {
        Path path = getBasePath().resolve(name);
        path.toFile().mkdirs();
        return path;
    }

    public static Path getVideosStoragePath() {
        return getPath("video_storage");
    }

    public static Path getUploadTemporalPath() {
        return getPath("upload");
    }


    public static Path getProcessingPath() {
        return getPath("video_processing");
    }

    public static Path getAudioProcessingPath() {
        return getPath("audio_processing");
    }

    public static Path getChunkStoragePath() {
        return getPath("chunks");
    }

    public static Path getCacheStoragePath() {
        return getPath("cache");
    }
}
