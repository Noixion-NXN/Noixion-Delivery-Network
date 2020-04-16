/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package controllers;


import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.RangeResults;
import play.mvc.Result;
import services.DHTService;
import services.kademlia.BlockNotFoundException;
import services.kademlia.KadKey;
import services.kademlia.KademliaOperationException;
import services.videos.VideoIndex;
import services.videos.VideoInputStream;
import services.videos.VideoResolutionIndex;
import utils.VideoTokenManager;
import utils.security.AccessTokenManager;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class VideoController extends Controller {

    @Inject
    public DHTService dht;

    @Inject
    HttpExecutionContext ec;

    private VideoTokenManager videoTokenManager;

    public VideoController() {
        videoTokenManager = VideoTokenManager.getInstance();
    }

    /**
     * Video previews.
     */
    public CompletionStage<Result> videoPreview(String videoIndexKey) {
        return CompletableFuture.supplyAsync(() -> {
            KadKey key;

            try {
                key = KadKey.fromHex(videoIndexKey);
            } catch (Exception ex) {
                return badRequest("Invalid HEX.");
            }

            if (!key.isValidIndexKey()) {
                return badRequest("Invalid index kad_key.");
            }

            try {
                VideoIndex vIndex = dht.getVideoIndex(key, false);

                KadKey prevKey = vIndex.getPreviewBlock();

                if (!prevKey.equals(KadKey.zero())) {
                    byte[] previewBytes = dht.readBlockFromDHT(prevKey);

                    return ok(previewBytes)
                            .withHeader(CONTENT_TYPE, "image/gif")
                            .withHeader(CONTENT_LENGTH, previewBytes.length + "");
                } else {
                    return notFound();
                }
            } catch (BlockNotFoundException ex) {
                return notFound();
            } catch (InterruptedException | KademliaOperationException | IOException ex) {
                ex.printStackTrace();
                return internalServerError(ex.getMessage());
            }
        }, ec.current());
    }

    /**
     * Video schema.
     */
    public CompletionStage<Result> videoSchema(String watchingToken) {
        return CompletableFuture.supplyAsync(() -> {
            String keyIndex = videoTokenManager.useToken(watchingToken);
            if (keyIndex == null) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            KadKey key;

            try {
                key = KadKey.fromHex(keyIndex);
            } catch (Exception ex) {
                return notFound("Invalid HEX").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            if (!key.isValidIndexKey()) {
                return notFound("Invalid kad_key").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            try {
                VideoIndex vIndex = dht.getVideoIndex(key, false);

                KadKey schemaKey = vIndex.getSchemaBlock();

                if (!schemaKey.equals(KadKey.zero())) {
                    byte[] schemaBytes = dht.readBlockFromDHT(schemaKey);
                    JsonNode json = Json.parse(new String(schemaBytes));

                    return ok(json).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                } else {
                    return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }
            } catch (BlockNotFoundException ex) {
                ex.printStackTrace();
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (Exception ex) {
                ex.printStackTrace();
                return internalServerError(ex.getMessage()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        }, ec.current());
    }

    /**
     * Video metadata.
     *
     * @param indexKey The video index kad_key.
     */
    public CompletionStage<Result> videoMetadata(String indexKey) {
        return CompletableFuture.supplyAsync(() -> {
            String keyIndex = indexKey;
            if (keyIndex == null) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            KadKey key;

            try {
                key = KadKey.fromHex(keyIndex);
            } catch (Exception ex) {
                return notFound("Invalid HEX").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }


            if (!key.isValidIndexKey()) {
                return notFound("Invalid kad_key").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            try {
                VideoIndex vIndex = dht.getVideoIndex(key, true);

                KadKey schemaKey = vIndex.getSchemaBlock();

                if (!schemaKey.equals(KadKey.zero())) {
                    byte[] schemaBytes = dht.readBlockFromDHT(schemaKey);
                    JsonNode json = Json.parse(new String(schemaBytes));

                    double duration = json.get("duration").asDouble();
                    String resolutions = "";
                    long size = 0;
                    for (VideoResolutionIndex res : vIndex.getResolutions()) {
                        if (!resolutions.equals("")) {
                            resolutions += ",";
                        }
                        resolutions += res.getResolutionName();
                        size += res.getMp4Blocks().size() * vIndex.getFixedBlockSize() + res.getWebmBlocks().size() * vIndex.getFixedBlockSize();
                    }

                    return ok(Json.newObject().put("duration", duration).put("resolutions", resolutions).put("size", size))
                            .withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                } else {
                    return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }
            } catch (BlockNotFoundException ex) {
                ex.printStackTrace();
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (Exception ex) {
                ex.printStackTrace();
                return internalServerError(ex.getMessage()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        }, ec.current());
    }

    /**
     * Gets a watching token for the video.
     */
    public Result getVideoWatchingToken(String videoIndexKey, String accessToken) {
        if (AccessTokenManager.getInstance().useToken(accessToken)) {
            String token = videoTokenManager.generateTokenForWatching(videoIndexKey);
            return ok(token).as("text/plain").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        } else {
            return forbidden().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }
    }


    public CompletionStage<Result> deleteVideo(String indexKey, String accessToken) {
        return CompletableFuture.supplyAsync(() -> {
            if (!AccessTokenManager.getInstance().useToken(accessToken)) {
                return forbidden().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
            String keyIndex = indexKey;
            if (keyIndex == null) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            KadKey key;

            try {
                key = KadKey.fromHex(keyIndex);
            } catch (Exception ex) {
                return notFound("Invalid HEX").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }


            if (!key.isValidIndexKey()) {
                return notFound("Invalid kad_key").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            try {
                VideoIndex vIndex = dht.getVideoIndex(key, true);

                dht.eraseVideoFromStorage(key, vIndex);

                return ok(key.toString()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (BlockNotFoundException ex) {
                ex.printStackTrace();
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (Exception ex) {
                ex.printStackTrace();
                return internalServerError(ex.getMessage()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        }, ec.current());
    }

    /**
     * Gets a MP4 video stream for native players.
     */
    public CompletionStage<Result> getVideoMp4Stream(Http.Request request, String watchingToken, String resolution) {
        return CompletableFuture.supplyAsync(() -> {
            String keyIndex = videoTokenManager.useToken(watchingToken);
            if (keyIndex == null) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            KadKey key;

            try {
                key = KadKey.fromHex(keyIndex);
            } catch (Exception ex) {
                return notFound("Invalid HEX").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            if (!key.isValidIndexKey()) {
                return notFound("Invalid kad_key").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
            try {
                VideoIndex vIndex = dht.getVideoIndex(key, true);
                VideoResolutionIndex res = vIndex.findResolution(resolution);

                if (res == null) {
                    if (vIndex.getResolutions().isEmpty()) {
                        return notFound("Resolution not found").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                    } else {
                        res = vIndex.getResolutions().get(vIndex.getResolutions().size() - 1);
                    }
                }

                VideoInputStream stream = new VideoInputStream(dht, vIndex.getFixedBlockSize(), res.getMp4Blocks());

                return RangeResults.ofStream(request, stream, stream.length()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*").withHeader(CONTENT_TYPE, "video/mp4");

            } catch (BlockNotFoundException ex) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (InterruptedException | KademliaOperationException | IOException ex) {
                ex.printStackTrace();
                return internalServerError(ex.getMessage()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        }, ec.current());
    }

    /**
     * Gets stream of HLS file.
     */
    public CompletionStage<Result> getHLS(Http.Request request, String watchingToken, String resolution, String file) {
        String ctype = "";
        if (file.endsWith(".m3u8")) {
            ctype = "video/x-mpegurl";
        } else if (file.endsWith(".ts")) {
            ctype = "video/mp2t";
        }

        final String contentType = ctype;

        return CompletableFuture.supplyAsync(() -> {
            String keyIndex = videoTokenManager.useToken(watchingToken);
            if (keyIndex == null) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            KadKey key;

            try {
                key = KadKey.fromHex(keyIndex);
            } catch (Exception ex) {
                return notFound("Invalid HEX").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }

            if (!key.isValidIndexKey()) {
                return notFound("Invalid kad_key").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
            try {
                VideoIndex vIndex = dht.getVideoIndex(key, true);
                VideoResolutionIndex res = vIndex.findResolution(resolution);

                if (res == null) {
                    if (vIndex.getResolutions().isEmpty()) {
                        return notFound("Resolution not found").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                    } else {
                        res = vIndex.getResolutions().get(vIndex.getResolutions().size() - 1);
                    }
                }

                if (!res.getHlsBlocks().containsKey(file)) {
                    return notFound("HLS file not found").withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }

                VideoInputStream stream = new VideoInputStream(dht, vIndex.getFixedBlockSize(), res.getHlsBlocks().get(file));

                return RangeResults.ofStream(request, stream, stream.length()).withHeader(CONTENT_TYPE, contentType).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (BlockNotFoundException ex) {
                return notFound().withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            } catch (InterruptedException | KademliaOperationException | IOException ex) {
                ex.printStackTrace();
                return internalServerError(ex.getMessage()).withHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            }
        }, ec.current());
    }
}