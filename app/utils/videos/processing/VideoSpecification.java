package utils.videos.processing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a video specification.
 */
public class VideoSpecification {
    private final String resolutionName;

    private final int width;
    private final int height;

    private final int bitrate;

    public VideoSpecification(String resolution) {
        switch (resolution.toLowerCase()) {
            case "144p":
                this.resolutionName = "144p";
                this.width = 256;
                this.height = 144;
                this.bitrate = 500;
                break;
            case "240p":
                this.resolutionName = "240p";
                this.width = 352;
                this.height = 240;
                this.bitrate = 800;
                break;
            case "360p":
                this.resolutionName = "360p";
                this.width = 480;
                this.height = 360;
                this.bitrate = 1200;
                break;
            case "480p":
                this.resolutionName = "480p";
                this.width = 858;
                this.height = 480;
                this.bitrate = 1500;
                break;
            case "720p":
                this.resolutionName = "720p";
                this.width = 1280;
                this.height = 720;
                this.bitrate = 4000;
                break;
            case "1080p":
                this.resolutionName = "1080p";
                this.width = 1920;
                this.height = 1080;
                this.bitrate = 8000;
                break;
            case "2160p":
            case "4k":
                this.resolutionName = "2160p";
                this.width = 3860;
                this.height = 2160;
                this.bitrate = 14000;
                break;
            default:
                throw new IllegalArgumentException("Invalid resolution name.");
        }
    }

    public String getResolutionName() {
        return resolutionName;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getSize() {
        return width * height;
    }

    public int getBitRate() {
        return this.bitrate;
    }

    public String getTaskName(String prefix) {
        return prefix + "_" + resolutionName.toUpperCase() + "_" + width + "X" + height;
    }

    public String toString() {
        return resolutionName + " (" + width + " x " + height + ")";
    }

    /*public FFmpegBuilder getEncondingCommand(UploadedVideo video) {
        if (this.getFormat().equalsIgnoreCase("webm")) {
            return new FFmpegBuilder()
                    .addInput(video.getOriginalFile().toPath().toAbsolutePath().toString())
                    .overrideOutputFiles(true)
                    .addOutput(video.getFragmentFilePattern(this).toPath().toAbsolutePath().toString())
                    .setVideoCodec("libvpx")
                    .setAudioCodec("libvorbis")
                    .setVideoResolution(this.getWidth(), this.getHeight())
                    .setFormat("segment")
                    .addExtraArgs("-segment_time", "" + video.fragmentSize)
                    .addExtraArgs("-reset_timestamps", "1")
                    .done();
        } else {
            return new FFmpegBuilder()
                    .addInput(video.getOriginalFile().toPath().toAbsolutePath().toString())
                    .overrideOutputFiles(true)
                    .addOutput(video.getFragmentFilePattern(this).toPath().toAbsolutePath().toString())
                    .setVideoResolution(this.getWidth(), this.getHeight())
                    .setFormat("segment")
                    .addExtraArgs("-segment_time", "" + video.fragmentSize)
                    .addExtraArgs("-reset_timestamps", "1")
                    .done();
        }
    }*/

    public List<String> getFragments(Path path, String extension) {
        List<String> result = new ArrayList<>();
        String[] files = path.toFile().list();
        for (String file : files) {
            if (file.endsWith("." + extension)) {
                result.add(file);
            }
        }
        return result;
    }
}
