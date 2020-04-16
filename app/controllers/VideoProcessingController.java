package controllers;

import models.VideoProcessingStatus;
import models.VideoUploadStatus;
import play.mvc.Controller;
import play.mvc.Result;
import services.AmazonProcessingService;
import services.VideoProcessingService;
import services.kademlia.KadKey;
import utils.security.AccessTokenManager;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Processing methods
 */
public class VideoProcessingController extends Controller {

    @Inject
    public VideoProcessingService videoProcessingService;

    @Inject
    public AmazonProcessingService amazonProcessingService;

    public Result startVideoProcessing(String accessToken, String uploadToken, String videoKey, String extraAudio) {
        // Starts processing the video
        try {
            KadKey.fromHex(videoKey);
        } catch (Exception ex) {
            return badRequest();
        }
        if (AccessTokenManager.getInstance().useToken(accessToken)) {
            VideoUploadStatus vUpload = VideoUploadStatus.findByToken(uploadToken);
            if (vUpload == null || !vUpload.uploaded) {
                return notFound("Video may be still uploading...");
            }
            VideoProcessingStatus vPro = new VideoProcessingStatus(videoKey, vUpload.options);
            vPro.extraAudio = extraAudio.equalsIgnoreCase("true");

            try {
                vPro.save();
            } catch (Exception ex) {
                return badRequest("Video with kad_key that already exists.");
            }

            if (amazonProcessingService.isEnabled()) {
                try {
                    amazonProcessingService.setupVideoProcessingJob(vPro.id, vUpload, videoKey, vPro.extraAudio);
                } catch (Exception e) {
                    e.printStackTrace();
                    return internalServerError();
                }
            } else {
                try {
                    Files.move(vUpload.getFile(), vPro.getOriginalVideoPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                    return internalServerError();
                }

                new Thread(() -> {
                    videoProcessingService.processVideo(vPro);
                }).start();
            }

            return ok("" + vPro.id);
        } else {
            return forbidden();
        }
    }

    public Result getVideoProcessingStatus(String videoKey) {
        VideoProcessingStatus vPro = VideoProcessingStatus.findByKey(videoKey);
        if (vPro == null) {
            return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }

        if (amazonProcessingService.isEnabled()) {
            return ok(amazonProcessingService.checkVideoStatus(vPro)).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        } else {
            return ok(videoProcessingService.checkVideoStatus(vPro)).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
    }

    public Result deleteVideoProcessing(String videoKey, String accessToken) {
        // Starts processing the video
        if (AccessTokenManager.getInstance().useToken(accessToken)) {
            VideoProcessingStatus vPro = VideoProcessingStatus.findByKey(videoKey);
            if (vPro == null) {
                return notFound();
            }

            if (amazonProcessingService.isEnabled()) {
                amazonProcessingService.stopProcessingVideo(vPro);
            } else {
                videoProcessingService.stopProcessingVideo(vPro);
            }

            return ok();
        } else {
            return forbidden();
        }
    }
}
