package controllers;

import models.VideoProcessingStatus;
import play.mvc.Controller;
import play.mvc.Result;
import services.AmazonProcessingService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

public class AudioController extends Controller {

    @Inject
    public AmazonProcessingService amazonProcessingService;

    public static String getAudioList(VideoProcessingStatus video) {
        String audios = "";
        int counter = 0;

        File[] files = new File(video.getAudioPath().toString()).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    if (counter > 0){
                        audios = audios.concat(",");
                    }
                    audios = audios.concat(file.getName());
                    counter++;
                }
            }
        }
        return audios;
    }

    public Result getAudio(String videoKey, String lgCode) {
        VideoProcessingStatus video = VideoProcessingStatus.findByKey(videoKey);
        if (video == null) {
            return notFound();
        }
        if (amazonProcessingService.isEnabled()) {
            try {
                byte[] audio = amazonProcessingService.getAudioFile(video.kad_key, lgCode);
                return ok(audio);
            } catch (IOException e) {
                return notFound();
            }
        } else {
            File audio = new File(video.getAudioPath().resolve(lgCode).toString());
            return ok(audio);
        }

    }


    public Result deleteAudios(String videoKey) {
        VideoProcessingStatus video = VideoProcessingStatus.findByKey(videoKey);
        if (video == null) {
            return notFound();
        }
        File dir = new File(video.getAudioPath().toString());
        deleteDir(dir);
        return ok();
    }

    private static void deleteDir(File dir) {
        if (!dir.exists()) { return; }

        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                deleteDir(f);  }
        }
        dir.delete();
    }

}
