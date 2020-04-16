package utils.videos.processing;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import play.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Video preview generator via FFMPEG.
 */
public class VideoPreviewBuilder extends Thread {
    public static final int START_TIME = 20; // 20 seconds to avoid intros
    public static final int SNIPPET_LENGTH = 1; // 1 seconds each snippet
    public static final int NUMBER_OF_SNIPPETS = 4; // 4 snippets for the preview
    public static final int VIDEO_SCALE = 240; // 240p

    private long videoId;
    private File videoFile;
    private File destination;
    private Path tempDir;
    private Path ffmpegBinary;
    private Path ffprobeBinary;

    public VideoPreviewBuilder(Path ffmpegBinary, Path ffprobeBinary, long videoId, File videoFile, File previewDestFile, Path tempDir) throws IOException {
        this.videoId = videoId;
        this.ffmpegBinary = ffmpegBinary;
        this.ffprobeBinary = ffprobeBinary;
        this.videoFile = videoFile;
        this.destination = previewDestFile;
        this.tempDir = tempDir;
    }

    @Override
    public void run() {
        try {
            generatePreview();
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.of(VideoPreviewBuilder.class).error("The video preview generation failed.");
        }
    }

    /**
     * Gnerates the video preview.
     *
     * @throws IOException If some I/O error occurs.
     */
    public void generatePreview() throws IOException, InterruptedException {
        FFmpeg fFmpeg = new FFmpeg(ffmpegBinary.toAbsolutePath().toString());
        FFprobe fFprobe = new FFprobe(ffprobeBinary.toAbsolutePath().toString());
        FFmpegExecutor executor = new FFmpegExecutor(fFmpeg, fFprobe);
        FFmpegProbeResult probeResult = fFprobe.probe(this.videoFile.toPath().toAbsolutePath().toString());

        // Check video duration
        if (probeResult.getFormat().duration < getMinVideoLengthInSeconds()) {
            Logger.of(VideoPreviewBuilder.class).warn("Could not generate preview. The video was too short. Video path: "
                    + this.videoFile.toPath().toAbsolutePath().toString());
            return;
        }

        // Generate snippets
        List<File> snippets = new ArrayList<>();
        PrintWriter pw = new PrintWriter(this.tempDir.resolve("snippets-" + videoId + ".txt").toFile());
        double interval = (probeResult.getFormat().duration - START_TIME) / NUMBER_OF_SNIPPETS;
        for (int i = 0; i < NUMBER_OF_SNIPPETS; i++) {
            double snippetStart = (i * interval) + START_TIME;
            Path snippetFile = this.tempDir.resolve("snippet-" + videoId + "-" + i + ".mp4");
            snippets.add(snippetFile.toFile());
            pw.println("file '" + snippetFile.toAbsolutePath().toString() + "'");
            FFmpegBuilder builder = new FFmpegBuilder()
                    .addInput(videoFile.toPath().toAbsolutePath().toString())
                    .setFormat("mp4")
                    .overrideOutputFiles(true)
                    .setStartOffset(new Double(snippetStart).longValue(), TimeUnit.SECONDS) // Start
                    .addOutput(snippetFile.toAbsolutePath().toString())
                    .addExtraArgs("-strict", "-2")
                    .setDuration(SNIPPET_LENGTH, TimeUnit.SECONDS).done();
            FFmpegJob job = executor.createJob(builder);
            job.run();
        }
        pw.close();

        // Concatenate snippets
        Path listFile = this.tempDir.resolve("snippets-" + videoId + ".txt").toAbsolutePath();
        String cmd = fFmpeg.getPath() + " -y -strict -2 -f concat -safe 0 -i "
                + listFile.toString() + " -vf scale=" + "160:90 " + this.destination.toPath().toAbsolutePath().toString();
        Runtime.getRuntime().exec(cmd).waitFor();

        //Remove snippets
        for (File snippet : snippets) {
            snippet.delete();
        }
        listFile.toFile().delete();
    }

    private int getMinVideoLengthInSeconds() {
        return START_TIME + (NUMBER_OF_SNIPPETS * SNIPPET_LENGTH);
    }
}
