package models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.ebean.Finder;
import io.ebean.Model;
import play.libs.Json;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Stores the information about a processing job.
 */
@Entity
public class VideoProcessingJob extends Model {

    private static Finder<Long, VideoProcessingJob> find = new Finder<>(VideoProcessingJob.class);

    /**
     * Sequence ID.
     */
    @Id
    public Long id;

    public String job_id;

    public String original_file;

    public String audioFiles;

    public long last_checked;

    public String task;

    public double progress;

    public double remainingTime;

    public VideoProcessingJob(Long id, String original_file) {
        this.id = id;
        this.job_id = null;
        this.audioFiles = "";
        this.last_checked = System.currentTimeMillis();
        this.task = "IDLE";
        this.progress = 0;
        this.remainingTime = 0;
        this.original_file = original_file;
    }

    public static VideoProcessingJob findById(long id) {
        return find.byId(id);
    }

    public String getMp3AudioFiles() {
        String audios = "";
        try {
            ArrayNode array = (ArrayNode) Json.parse(this.audioFiles);
            for (JsonNode n : array) {
                String file = n.asText() + ".mp3";
                if (audios.length() > 0) {
                    audios  += ",";
                }
                audios += file;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
        return audios;
    }

    public String logsGroup(String jobDefinitionName) {
        if (this.job_id != null) {
            return jobDefinitionName + this.job_id;
        } else {
            return jobDefinitionName + "unknown";
        }
    }

}
