package services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import models.VideoProcessingJob;
import models.VideoProcessingStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Redis service, progress report and control
 */
public class RedisService extends JedisPubSub implements Runnable {

    public static final String REDIS_ROOM = "noixion_jobs";

    private JedisPool pool;
    private boolean closed;
    private Thread thread;

    private final AmazonProcessingService amazonProcessingService;

    public String getHost() {
        return host;
    }

    private String host;

    public int getPort() {
        return port;
    }

    private int port;

    public RedisService(AmazonProcessingService amazonProcessingService, String host, int port) {
        this.amazonProcessingService = amazonProcessingService;
        this.host = host;
        this.port = port;
        pool = new JedisPool(host, port);
    }

    @Override
    public synchronized void onMessage(String channel, String message) {
        if (channel == null || !channel.equals(REDIS_ROOM)) return;
        try {
            String[] args = message.split("\\|");
            switch (args[0]) {
                case "P":
                {
                    // Progress report
                    Long jobId = Long.parseLong(args[1]);
                    String task = args[2];
                    Double progress = Double.parseDouble(args[3]);
                    Double remaining = Double.parseDouble(args[4]);

                    VideoProcessingJob job = VideoProcessingJob.findById(jobId);

                    if (job != null) {
                        job.task = task;
                        job.progress = progress;
                        job.remainingTime = remaining;
                        job.save();
                    }
                }
                    break;
                case "F":
                {
                    // Job failure
                    Long jobId = Long.parseLong(args[1]);
                    String errorMsg = args[2];

                    VideoProcessingJob job = VideoProcessingJob.findById(jobId);

                    if (job != null) {
                        try {
                            // Free original video
                            amazonProcessingService.deleteFile(job.original_file);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    VideoProcessingStatus video = VideoProcessingStatus.findById(jobId);

                    if (video != null) {
                        video.errorMessage = errorMsg;
                        video.markError();
                    }
                }
                break;
                case "S":
                {
                    // Job success
                    Long jobId = Long.parseLong(args[1]);
                    String indexKey = args[2];
                    String arrayLangs = args[3];

                    VideoProcessingJob job = VideoProcessingJob.findById(jobId);

                    if (job != null) {
                        job.task = "success";
                        job.progress = 1;
                        job.remainingTime = 0;
                        job.audioFiles = arrayLangs;
                        job.save();

                        try {
                            // Free original video
                            amazonProcessingService.deleteFile(job.original_file);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    VideoProcessingStatus video = VideoProcessingStatus.findById(jobId);

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

    public void killJob(long job) {
        this.pool.getResource().publish(REDIS_ROOM, "K|" + job);
    }

    @Override
    public void run() {
        while (!this.closed) {
            try {
                Jedis connection = pool.getResource();
                connection.subscribe(this, REDIS_ROOM);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        this.closed = true;
        this.pool.close();
    }
}
