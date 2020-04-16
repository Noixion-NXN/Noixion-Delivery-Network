package utils.videos.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.VideoProcessingOptions;
import models.VideoProcessingStatus;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import play.libs.Json;
import services.VideoProcessingService;
import services.kademlia.KadKey;
import services.videos.VideoIndex;
import services.videos.VideoResolutionIndex;
import utils.StorageConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Video processing task.
 */
public class VideoProcessingTask extends Thread {

    private VideoProcessingService service;

    private Path ffmpegBinary;
    private Path ffprobeBinary;
    private VideoProcessingStatus video;

    private boolean interrupted;

    private long timestampStart;

    private String task_name;
    private long task_start;
    private double task_progress;

    private String error_reason = "VIDEO_FORMAT";

    public VideoProcessingTask(Path ffmpegBinary, Path ffprobeBinary, VideoProcessingStatus video, VideoProcessingService service) {
        this.service = service;
        this.timestampStart = System.currentTimeMillis();
        this.ffmpegBinary = ffmpegBinary;
        this.ffprobeBinary = ffprobeBinary;
        this.video = video;
        this.task_name = "IDLE";
        this.task_progress = 0;
        this.interrupted = false;
    }

    @Override
    public void interrupt() {
        this.interrupted = true;
        super.interrupt();
    }

    public synchronized double estimateRemainingTime() {
        double taskTime =  System.currentTimeMillis() - this.task_start;
        if (task_progress == 0) {
            return 0;
        }
        return ((taskTime) / task_progress) * (1 - task_progress);
    }

    public synchronized JsonNode getTaskProgressReport() {
        ObjectNode node = Json.newObject();
        node.put("status", "in_progress");
        node.put("progress", this.task_progress);
        node.put("time", System.currentTimeMillis() - this.timestampStart);
        node.put("task", this.task_name);
        node.put("remaining_time", this.estimateRemainingTime());
        return node;
    }

    @Override
    public void run() {
        try {
            if (!video.processed) {
                processVideo();
            }
            storeVideoDHT();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Processing video failed.");
            video.deleteAllFiles();
            video.errorMessage = ex.getMessage();
            video.markError();
        }

    }

    private void processVideo() throws Exception {
        FFmpeg fFmpeg = new FFmpeg(ffmpegBinary.toAbsolutePath().toString());
        FFprobe fFprobe = new FFprobe(ffprobeBinary.toAbsolutePath().toString());
        FFmpegExecutor executor = new FFmpegExecutor(fFmpeg, fFprobe);

        ObjectNode schema = Json.newObject();
        ObjectNode resolutionsSchema = Json.newObject();

        FFmpegProbeResult probeResult = fFprobe.probe(video.getOriginalVideoPath().toAbsolutePath().toString());
        boolean hasVideoStream = false;
        int width = 0;
        int height = 0;
        for (FFmpegStream stream : probeResult.getStreams()) {
            if (stream.codec_type == FFmpegStream.CodecType.VIDEO && (width * height) < (stream.width * stream.height)) {
                width = stream.width;
                height = stream.height;
                hasVideoStream = true;
            }
        }
        if (!hasVideoStream) {
            throw new Exception("The given file does not contain any video streams.");
        }

        long time = new Double(Math.floor(probeResult.getFormat().duration * 1000 * 1000 * 1000)).longValue();
        VideoSpecification[] specs = StorageConfiguration.getSpecificationsForVideo(width, height);

        //ffprobe command to pick up your output and process the number of audios the video has.
        //System.out.println(ffprobeBinary.toAbsolutePath().toString() + " " + video.getOriginalVideoPath().toAbsolutePath().toString());
        Process process = Runtime.getRuntime().exec(ffprobeBinary.toAbsolutePath().toString() + " " + video.getOriginalVideoPath().toAbsolutePath().toString());
        InputStream inputstream = process.getErrorStream();
        BufferedInputStream bufferedinputstream = new BufferedInputStream(inputstream);
        String[] tokens = IOUtils.toString(bufferedinputstream, "UTF-8").split("\n");
        ArrayList<String> streams = new ArrayList<>();
        for (String token : tokens) {
             if (token.contains("Audio:")) {
                streams.add(StringUtils.substringBetween(token, "(", ")"));
            }
        }


        if (video.vid_options == VideoProcessingOptions.BEST_QUALITY) {
            specs = StorageConfiguration.getBestSpecification(specs);
        }

        video.duration = probeResult.getFormat().duration;
        video.markStarted();

        schema.put("duration", video.duration);

        if (this.interrupted) {
            throw new Exception("Task manually interrupted");
        }

        // Filter: TODO

        if (this.interrupted) {
            throw new Exception("Task manually interrupted");
        }

        if (!video.processed) {

        }

        // Spawn task for the preview
        System.out.println("Generating Preview...");
        this.task_name = "preview";
        task_start = System.currentTimeMillis();
        this.task_progress = 0;
        VideoPreviewBuilder vpb = new VideoPreviewBuilder(this.ffmpegBinary, this.ffprobeBinary, video.id, video.getOriginalVideoPath().toFile(), video.getPreview().toFile(), video.getTemporalPath());
        vpb.start();
        vpb.join();

        if (this.interrupted) {
            throw new Exception("Task manually interrupted");
        }

        FFmpegBuilder ff_builder = new FFmpegBuilder()
                .addInput(video.getOriginalVideoPath().toAbsolutePath().toString())
                .overrideOutputFiles(true);

        if (video.extraAudio) {
            for (int i=0; i<streams.size(); i++) {
                ff_builder.addOutput(video.getAudioPath().toAbsolutePath().resolve(streams.get(i) + ".mp3").toString())
                        .addExtraArgs("-map", "0:a:" + i)
                        .done();
            }
        }

        // Encode and Fragment video
        System.out.println("Encoding Video...");
        for (final VideoSpecification spec : specs) {
            // MP4
            ff_builder.addOutput(video.getVideoMP4(spec).toAbsolutePath().toString())
                    .setVideoResolution(spec.getWidth(), spec.getHeight())
                    .setFormat("mp4")
                    .addExtraArgs("-vcodec", "libx264")
                    .addExtraArgs("-preset", StorageConfiguration.MP4_SPEED)
                    .addExtraArgs("-strict", "-2")
                    .done();


            // HLS
            ff_builder.addOutput(video.getPathHLS(spec).resolve("index.m3u8").toAbsolutePath().toString())
                    .setVideoResolution(spec.getWidth(), spec.getHeight())
                    .setFormat("hls")
                    .addExtraArgs("-profile:v", "baseline")
                    .addExtraArgs("-level", "3.0")
                    .addExtraArgs("-pix_fmt", "yuv420p")
                    .addExtraArgs("-hls_time", "10")
                    .addExtraArgs("-hls_list_size", "0")
                    .addExtraArgs("-strict", "-2")
                    .done();
        }

        List<String> args = ff_builder.build();
        String cmd = "ffmpeg";
        for (String arg : args) {
            cmd += " " + arg;
        }
        System.out.println(cmd);

        task_name = "encode";
        task_start = System.currentTimeMillis();
        task_progress = 0;

        FFmpegJob job = executor.createJob(ff_builder, progress -> {
            System.out.println("[" + ((double) progress.out_time_ns * 100.0 / time) + "] - VIDEO PROCESSING (" + video.kad_key + ") [" + video.id + "]");
            task_progress = (double) progress.out_time_ns / time;
            if (interrupted) {
                fFmpeg.killProcesses();
            }
        });
        job.run();
        task_progress = 1;

        if (this.interrupted) {
            throw new Exception("Task manually interrupted");
        }

        // Hash fragments
        System.out.println("Hashing files...");
        for (VideoSpecification spec : specs) {
            ObjectNode hlsIntegrity = Json.newObject();

            File[] hlsFiles = video.getPathHLS(spec).toFile().listFiles();
            for (File file : hlsFiles) {
                String fileName = file.getName();
                String hash = getBase64Hash(video.getHLSFile(spec, fileName));
                hlsIntegrity.put(fileName, hash);
            }

            ObjectNode schemaRes = Json.newObject();
            schemaRes.put("name", spec.getResolutionName());
            schemaRes.put("width", spec.getWidth());
            schemaRes.put("height", spec.getHeight());
            schemaRes.set("hls", hlsIntegrity);

            resolutionsSchema.set(spec.getResolutionName(), schemaRes);
        }



        System.out.println("Clearing files...");

        System.out.println("Storing schema...");
        schema.set("resolutions", resolutionsSchema);
        PrintWriter pw = new PrintWriter(video.getSchemaFile().toFile());
        pw.print(Json.stringify(schema));
        pw.close();

        video.processed = true;
        video.save();

        video.deleteOriginalVideo();
    }

    private void storeVideoDHT() throws Exception  {
        KadKey videoKey = KadKey.fromHex(video.kad_key);
        List<VideoSpecification> resolutions = new ArrayList<>();

        File[] files = video.getVideoProcessingPath().toFile().listFiles();

        for (File f : files) {
            if (f.isDirectory()) {
                try {
                    resolutions.add(new VideoSpecification(f.getName()));
                } catch (Exception ex) {}
            }
        }

        task_name = "store";
        task_start = System.currentTimeMillis();

        long totalSizeToStore = 0;
        for (VideoSpecification vp : resolutions) {
            totalSizeToStore += video.getVideoMP4(vp).toFile().length() + video.getVideoWebM(vp).toFile().length();
        }
        long totalSizeStored = 0;


        // Store the entire video in DHT, divide by chunks each resolution

        long videoPart = 1;

        VideoIndex index = new VideoIndex();

        // Store schema
        index.setSchemaBlock(this.service.dht.storeVideoBlock(videoKey, Files.readAllBytes(video.getSchemaFile()), videoPart++));

        //Store preview, if exists
        if (video.getPreview().toFile().exists()) {
            index.setPreviewBlock(this.service.dht.storeVideoBlock(videoKey, Files.readAllBytes(video.getPreview()), videoPart++));
        }

        // Store resolutions
        byte[] buffer = new byte[VideoIndex.FIXED_VIDEO_BLOCK_SIZE];
        byte[] trueData;
        for (VideoSpecification vp : resolutions) {
            VideoResolutionIndex vri = new VideoResolutionIndex(vp.getResolutionName());
            DataInputStream din;
            int bytesRead = 0;

            // Store MP4
            din = new DataInputStream(new FileInputStream(video.getVideoMP4(vp).toFile()));

            do {
                bytesRead = din.read(buffer);
                if (bytesRead > 0) {
                    trueData = Arrays.copyOfRange(buffer, 0, bytesRead);
                    vri.getMp4Blocks().add(this.service.dht.storeVideoBlock(videoKey, trueData, videoPart++));
                    totalSizeStored += bytesRead;
                }

                if (this.interrupted) {
                    throw new Exception("Task manually interrupted");
                }
            } while (bytesRead > 0);
            din.close();

            // Store HLS
            File[] hlsFiles = video.getPathHLS(vp).toFile().listFiles();
            for (File file : hlsFiles) {
                String fileName = file.getName();
                List<KadKey> fileKeys = new ArrayList<>();

                din = new DataInputStream(new FileInputStream(file));

                do {
                    bytesRead = din.read(buffer);
                    if (bytesRead > 0) {
                        trueData = Arrays.copyOfRange(buffer, 0, bytesRead);
                        fileKeys.add(this.service.dht.storeVideoBlock(videoKey, trueData, videoPart++));
                        totalSizeStored += bytesRead;
                    }

                    if (this.interrupted) {
                        throw new Exception("Task manually interrupted");
                    }
                } while (bytesRead > 0);
                din.close();

                vri.getHlsBlocks().put(fileName, fileKeys);
            }


            // Save into index
            index.getResolutions().add(vri);
        }

        // Finally, store index
        KadKey videoIndexKey = this.service.dht.storeVideoBlock(videoKey, index.serialize(), 0);
        video.setIndexChunk(videoIndexKey.toString());
        video.markReady();
        video.deleteAllFiles();

        System.out.println("Done! / Stored in DHT / Key: " + video.getIndexChunk());
        this.service.finishProcessVideo(video);
    }

    private String getBase64Hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(data);
        return Base64.encodeBase64String(digest.digest());
    }
}
