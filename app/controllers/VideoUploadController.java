package controllers;

import models.VideoProcessingOptions;
import models.VideoUploadStatus;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import resumable.HttpUtils;
import resumable.ResumableInfo;
import resumable.ResumableInfoStorage;
import services.AmazonProcessingService;
import utils.StorageConfiguration;
import utils.security.AccessTokenManager;
import utils.videos.processing.UploadConcurrencyControl;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Video Upload controller. Video Upload process.
 * Post upload (simple)
 * Resumable upload (big files)
 */
public class VideoUploadController extends Controller {

    @Inject
    public AmazonProcessingService amazonProcessingService;

    /**
     * Generates a new upload token.
     *
     * @param accessToken Access token.
     * @return The generated upload token as text/plain.
     */
    public Result generateUploadToken(Http.Request request, String accessToken) {
        VideoProcessingOptions opts = VideoProcessingOptions.ALL_QUALITIES;
        if (request.queryString().containsKey("single_quality")) {
            opts = VideoProcessingOptions.BEST_QUALITY;
        }
        if (AccessTokenManager.getInstance().useToken(accessToken)) {
            VideoUploadStatus video = VideoUploadStatus.create(opts);
            return ok(video.token).as("text/plain");
        } else {
            return forbidden();
        }
    }

    /**
     * Simple upload by POST
     */
    public Result postUpload(Http.Request request, String token) {
        VideoUploadStatus video;
        video = VideoUploadStatus.findByToken(token);
        if (video == null) {
            //System.out.println("Video not found");
            return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
        final Http.MultipartFormData<play.libs.Files.TemporaryFile> formData = request.body().asMultipartFormData();
        if (formData == null) {
            return badRequest().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
        final Http.MultipartFormData.FilePart<play.libs.Files.TemporaryFile> filePartVideo = formData.getFile("file");
        if (filePartVideo == null) {
            return badRequest().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
        File videoFile = filePartVideo.getRef().path().toFile();

        try {
            Files.move(videoFile.toPath(), video.getFile(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            ex.printStackTrace();
            return internalServerError().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }

        video.finishUpload(amazonProcessingService);

        return ok(Json.newObject().put("code", 0)).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    }

    /**
     * Upload chuck by chunk
     */
    public Result upload(Http.Request request, String token) {
        VideoUploadStatus video;
        video = VideoUploadStatus.findByToken(token);
        if (video == null) {
            //System.out.println("Video not found");
            return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
        Object sc = UploadConcurrencyControl.getInstance().getCriticalSection(video.id);
        try {
            synchronized (sc) {
                video = VideoUploadStatus.findById(video.id);
                if (video == null) {
                    //System.out.println("Video not found");
                    return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }
                if (video.uploaded) {
                    return ok("All finished.").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }
                int resumableChunkNumber = getResumableChunkNumber(request);

                ResumableInfo info = getResumableInfo(request, video);

                RandomAccessFile raf = new RandomAccessFile(info.resumableFilePath, "rw");

                //Seek to position
                raf.seek((resumableChunkNumber - 1) * (long) info.resumableChunkSize);

                //Save to file
                InputStream is = new FileInputStream(request.body().asRaw().asFile());
                long readed = 0;
                long content_length = request.body().asRaw().size();
                byte[] bytes = new byte[1024 * 100];
                while (readed < content_length) {
                    int r = is.read(bytes);
                    if (r < 0) {
                        break;
                    }
                    raf.write(bytes, 0, r);
                    readed += r;
                }
                raf.close();
                is.close();

                //Mark as uploaded.
                info.uploadedChunks.add(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber));
                if (info.checkIfUploadFinished()) { //Check if all chunks uploaded, and change filename
                    // Rename upload file
                    //System.out.println("Finished");
                    video.getTemporalFile().toFile().renameTo(video.getFile().toFile());
                    video.getTemporalFile().toFile().delete();
                    video.finishUpload(amazonProcessingService);
                    ResumableInfoStorage.getInstance().remove(info);
                    UploadConcurrencyControl.getInstance().leftCriticalSection(video.id);
                    return ok("All finished.").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                } else {
                    UploadConcurrencyControl.getInstance().leftCriticalSection(video.id);
                    return ok("Upload").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }
            }
        } catch (Exception e) {
            UploadConcurrencyControl.getInstance().leftCriticalSection(video.id);
            e.printStackTrace();
            return internalServerError().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
    }

    /**
     * Gets the upload status
     */
    public Result uploadStatus(Http.Request request, String token) {
        VideoUploadStatus video = VideoUploadStatus.findByToken(token);
        if (video == null || video.uploaded) {
            return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
        try {
            int resumableChunkNumber = getResumableChunkNumber(request);

            ResumableInfo info = getResumableInfo(request, video);

            if (info.uploadedChunks.contains(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber))) {
                return ok("Uploaded.").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*"); //This KadBlock has been Uploaded.
            } else {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
    }

    /**
     * Gets Resumable configuration
     */
    public Result resumableConfig(String token) {
        return noContent().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    }

    /* Private methods */

    private int getResumableChunkNumber(Http.Request request) {
        return HttpUtils.toInt(request.getQueryString("resumableChunkNumber"), -1);
    }

    private ResumableInfo getResumableInfo(Http.Request request, VideoUploadStatus uploaded) throws Exception {
        StorageConfiguration.load();
        int resumableChunkSize = HttpUtils.toInt(request.getQueryString("resumableChunkSize"), -1);
        long resumableTotalSize = HttpUtils.toLong(request.getQueryString("resumableTotalSize"), -1);
        String resumableIdentifier = request.getQueryString("resumableIdentifier");
        String resumableFilename = request.getQueryString("resumableFilename");
        String resumableRelativePath = request.getQueryString("resumableRelativePath");
        //Here we add a ".temp" to every upload file to indicate NON-FINISHED
        String resumableFilePath = uploaded.getTemporalFile().toString();

        ResumableInfoStorage storage = ResumableInfoStorage.getInstance();

        ResumableInfo info = storage.get(resumableChunkSize, resumableTotalSize,
                resumableIdentifier, resumableFilename, resumableRelativePath, resumableFilePath);
        if (!info.vaild()) {
            storage.remove(info);
            throw new Exception("Invalid request params.");
        }
        return info;
    }


}
