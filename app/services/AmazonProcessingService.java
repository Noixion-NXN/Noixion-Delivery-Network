package services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.AWSSessionCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.AWSBatchClientBuilder;
import com.amazonaws.services.batch.model.*;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.VideoProcessingJob;
import models.VideoProcessingStatus;
import models.VideoUploadStatus;
import org.apache.commons.io.IOUtils;
import play.Logger;
import play.libs.Json;
import services.kademlia.BlockNotFoundException;
import utils.StorageConfiguration;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static controllers.AudioController.getAudioList;

/**
 * Processes videos using AWS batch
 */
@Singleton
public class AmazonProcessingService {

    private final boolean enabled;
    private final String jobDefinition;
    private final String jobQueue;
    private final Regions region;
    private final String bucket;

    private final String authId;
    private final String authKey;

    private RedisService redis;

    public AmazonProcessingService() {
        Config config = ConfigFactory.load();

        if (config.hasPath("s3.batch.enabled") && config.getBoolean("s3.batch.enabled")) {
            enabled = true;

            jobDefinition = config.getString("s3.batch.job.definition");
            jobQueue = config.getString("s3.batch.job.queue");
            bucket = config.getString("s3.bucket");

            authId = config.getString("s3.batch.auth.id");
            authKey = config.getString("s3.batch.auth.key");

            region = Regions.fromName(config.getString("s3.region"));

            this.redis = new RedisService(this, "127.0.0.1", 6379);
        } else {
            enabled = false;

            jobDefinition = "";
            jobQueue = "";
            bucket = "";

            authId = "";
            authKey = "";

            region = Regions.DEFAULT_REGION;

            this.redis = null;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public byte[] getAudioFile(String videoKey, String lang) throws IOException {
        return getFile("audios/" + videoKey + "/" + lang + ".mp3");
    }

    public byte[] getFile(String key) throws IOException {
        Regions clientRegion = this.region;
        String bucketName = this.bucket;

        S3Object fullObject = null;
        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(clientRegion)
                    .withCredentials(new ProfileCredentialsProvider())
                    .build();

            // Get an object and print its contents.
            //System.out.println("[S3][GET] " + key.toString());
            fullObject = s3Client.getObject(new GetObjectRequest(bucketName, key.toString()));

            if (fullObject == null) {
                //System.out.println("Full object is null for " + key.toString());
                throw new BlockNotFoundException();
            }

            byte[] block = IOUtils.toByteArray(fullObject.getObjectContent());
            fullObject.close();

            return block;
        } catch (Exception e) {
            //e.printStackTrace();
            Logger.of(S3StorageService.class).warn("Block not found (s3): " + key + " / " + e.getMessage());
        } finally {
            // To ensure that the network connection doesn't remain open, close any open input streams.
            if (fullObject != null) {
                fullObject.close();
            }
        }

        throw new IOException("Not found");
    }

    public String storeFileAndDestroy(String token, File file) throws IOException {
        String key = "upload/" + token;
        storeFile(key, file);
        return key;
    }

    public void storeFile(String key, File file) throws IOException {
        Regions clientRegion = this.region;
        String bucketName = this.bucket;

        try {
            //This code expects that you have AWS credentials set up per:
            // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(clientRegion)
                    .build();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.length());
            metadata.setContentType("application/octet-stream");

            // Upload a text string as a new object.
            s3Client.putObject(bucketName, key, new FileInputStream(file), metadata);
        } catch (AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace();
            throw new IOException("Could not store kad_key in S3 bucket.");
        } catch (SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
            throw new IOException("Could not store kad_key in S3 bucket.");
        }
    }

    public void deleteOriginalFile(String token) {
        String key = "upload/" + token;
        deleteFile(key);
    }

    public void deleteFile(String key) {
        Regions clientRegion = this.region;
        String bucketName = this.bucket;
        String keyName = key;

        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new ProfileCredentialsProvider())
                    .withRegion(clientRegion)
                    .build();

            s3Client.deleteObject(new DeleteObjectRequest(bucketName, keyName));
        } catch (AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace();
        } catch (SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }
    }

    public VideoProcessingJob setupVideoProcessingJob(Long id, VideoUploadStatus uploadedVideo, String videoKey, boolean extraAudios) throws Exception {
        Regions clientRegion = this.region;
        String bucketName = this.bucket;

        VideoProcessingJob job = new VideoProcessingJob(id, "upload/" + uploadedVideo.token);
        job.save();

        //This code expects that you have AWS credentials set up per:
        // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html

        AWSBatch batchClient = AWSBatchClientBuilder.standard()
                .withRegion(clientRegion)
                .build();

        SubmitJobRequest request = new SubmitJobRequest();

        request.setJobDefinition(this.jobDefinition);
        request.setJobName("noixion-job-" + videoKey);
        request.setJobQueue(this.jobQueue);

        List<KeyValuePair> env = new ArrayList<>();

        env.add(new KeyValuePair().withName("JOB_ID").withValue("" + job.id));
        env.add(new KeyValuePair().withName("VIDEO_KEY").withValue(videoKey));
        env.add(new KeyValuePair().withName("BUCKET_NAME").withValue(bucketName));
        env.add(new KeyValuePair().withName("ORIGINAL_VIDEO").withValue("upload/" + uploadedVideo.token));
        env.add(new KeyValuePair().withName("REDIS_HOST").withValue(this.redis.getHost()));
        env.add(new KeyValuePair().withName("REDIS_PORT").withValue("" + this.redis.getPort()));
        env.add(new KeyValuePair().withName("AWS_REGION").withValue("" + this.region.getName()));

        env.add(new KeyValuePair().withName("AWS_ACCESS_KEY_ID").withValue(this.authId));
        env.add(new KeyValuePair().withName("AWS_SECRET_ACCESS_KEY").withValue(this.authKey));
        env.add(new KeyValuePair().withName("MP4_SPEED").withValue(StorageConfiguration.MP4_SPEED));
        env.add(new KeyValuePair().withName("VIDEO_RESOLUTIONS").withValue(StorageConfiguration.getResolutionsList()));
        env.add(new KeyValuePair().withName("EXTRACT_AUDIOS").withValue(extraAudios ? "TRUE" : "FALSE"));

        ContainerOverrides overrides = new ContainerOverrides();
        overrides.setEnvironment(env);

        request.setContainerOverrides(overrides);

        SubmitJobResult result = batchClient.submitJob(request);

        String jobId = result.getJobId();

        job.job_id = jobId;
        job.save();

        Logger.of(AmazonProcessingService.class).info("[AWS Batch] Created job " + jobId + " to process video " + videoKey + ", originally uploaded as S3:/upload/" + uploadedVideo.token);

        return job;
    }

    public void updateProcessingJob(VideoProcessingStatus video, VideoProcessingJob job) {
        if (video.error || video.ready) {
            return; // The task already finished
        }
        if (job.job_id == null) {
            // Nothing to do
            return;
        }

        Regions clientRegion = this.region;

        AWSBatch batchClient = AWSBatchClientBuilder.standard()
                .withRegion(clientRegion)
                .build();

        List<String> jobs = new ArrayList<>();
        jobs.add(job.job_id);
        DescribeJobsRequest describeJobsRequest = new DescribeJobsRequest().withJobs(jobs);


        JobDetail detail = batchClient.describeJobs(describeJobsRequest).getJobs().get(0);

        if (detail.getStatus().equalsIgnoreCase("SUCCEEDED") || detail.getStatus().equalsIgnoreCase("RUNNING")) {
            // Fetch log to update status
            AWSLogs logsClient = AWSLogsClientBuilder.standard()
                    .withRegion(clientRegion)
                    .build();

            String logsQueue = null;


            if (detail.getContainer() != null) {
                logsQueue = detail.getContainer().getLogStreamName();
            } else if (!detail.getAttempts().isEmpty()) {
                logsQueue = detail.getAttempts().get(0).getContainer().getLogStreamName();
            }

            if (logsQueue == null) {
                return;
            }

            //System.out.println("[" + clientRegion.getName() + "] -> " + "Fetching logs from: " + job.logsGroup(this.logsQueue));

            GetLogEventsRequest getLogEventsRequest = new GetLogEventsRequest()
                    .withLogGroupName("/aws/batch/job")
                    .withLogStreamName(logsQueue)
                    .withStartFromHead(false)
                    .withLimit(10);

            GetLogEventsResult result = logsClient.getLogEvents(getLogEventsRequest);

            for (OutputLogEvent outputLogEvent : result.getEvents()) {
                String msg = outputLogEvent.getMessage();

                //System.out.println("[MSG] " + msg);

                if (msg.startsWith("[LOG/REPORT]")) {
                    msg = msg.substring("[LOG/REPORT]".length()).trim().substring(1);
                    //System.out.println("[PARSING] " + msg);
                    onJobMessage(video, job, msg);
                }
            }

            video.save();
            job.save();
        } else if (detail.getStatus().equalsIgnoreCase("FAILED")) {
            try {
                // Free original video
                this.deleteFile(job.original_file);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            video.errorMessage = detail.getStatusReason();
            video.markError();
        }
    }

    public synchronized void onJobMessage(VideoProcessingStatus video, VideoProcessingJob job, String message) {
        try {
            String[] args = message.split("\\|");
            switch (args[0]) {
                case "P": {
                    // Progress report
                    String task = args[2];
                    Double progress = Double.parseDouble(args[3]);
                    Double remaining = Double.parseDouble(args[4]);

                    if (job != null) {
                        job.task = task;
                        job.progress = progress;
                        job.remainingTime = remaining;
                    }
                }
                break;
                case "F": {
                    // Job failure
                    String errorMsg = args[2];

                    if (job != null) {
                        try {
                            // Free original video
                            this.deleteFile(job.original_file);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    if (video != null) {
                        video.errorMessage = errorMsg;
                        video.markError();
                    }
                }
                break;
                case "S": {
                    // Job success
                    String indexKey = args[2];
                    String arrayLangs = args[3];

                    if (job != null) {
                        job.task = "success";
                        job.progress = 1;
                        job.remainingTime = 0;
                        job.audioFiles = arrayLangs;

                        try {
                            // Free original video
                            this.deleteFile(job.original_file);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    if (video != null) {
                        video.setIndexChunk(indexKey);
                        video.markReady();
                    }
                }
                break;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    public void stopProcessingVideo(VideoProcessingStatus video) {
        VideoProcessingJob job = VideoProcessingJob.findById(video.id);
        Regions clientRegion = this.region;

        // Cancel job
        if (job != null && job.job_id != null) {
            AWSBatch batchClient = AWSBatchClientBuilder.standard()
                    .withRegion(clientRegion)
                    .build();

            batchClient.cancelJob(new CancelJobRequest().withJobId(job.job_id).withReason("Manually cancelled by video publisher (Via Noixion)."));
        }

        // Delete uploaded file (original)
        if (job != null) {
            this.deleteFile(job.original_file);
        }

        video.markError();
    }


    public JsonNode checkVideoStatus(VideoProcessingStatus video) {
        VideoProcessingJob job = VideoProcessingJob.findById(video.id);
        if (job == null) {
            return Json.newObject().put("status", "unknown");
        }
        try {
            // Update if required
            this.updateProcessingJob(video, job);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (video.ready) {
            return Json.newObject().put("status", "ready").put("index", video.getIndexChunk())
                    .put("audioList", job.getMp3AudioFiles());
        } else if (video.error) {
            return Json.newObject().put("status", "error").put("message", video.errorMessage);
        } else {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("status", "in_progress");
            node.put("progress", job.progress);
            node.put("time", System.currentTimeMillis() - job.last_checked);
            node.put("task", job.task);
            node.put("remaining_time", job.remainingTime);
            return node;
        }
    }
}
