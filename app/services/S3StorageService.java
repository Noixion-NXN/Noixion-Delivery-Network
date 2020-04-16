package services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.IOUtils;
import play.Logger;
import services.kademlia.BlockNotFoundException;
import services.kademlia.KadKey;
import services.kademlia.KademliaConfiguration;

import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

@Singleton
public class S3StorageService {
    private final Regions region;
    private final String bucket;

    public S3StorageService() {
        Config config = ConfigFactory.load();

        if (config.hasPath("s3.region")) {
            region = Regions.fromName(config.getString("s3.region"));
        } else {
            region = Regions.DEFAULT_REGION;
        }

        bucket = config.getString("s3.bucket");
    }

    public byte[] read(KadKey key) throws IOException, BlockNotFoundException {
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
            Logger.of(S3StorageService.class).warn("Block not found (s3): " + key.toString() + " / " + e.getMessage());
        } finally {
            // To ensure that the network connection doesn't remain open, close any open input streams.
            if (fullObject != null) {
                fullObject.close();
            }
        }

        throw new BlockNotFoundException();
    }

    public void store(KadKey key, byte[] content) throws IOException {
        Regions clientRegion = this.region;
        String bucketName = this.bucket;

        try {
            //This code expects that you have AWS credentials set up per:
            // https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/setup-credentials.html
            AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                    .withRegion(clientRegion)
                    .build();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/octet-stream");

            // Upload a text string as a new object.
            s3Client.putObject(bucketName, key.toString(), new ByteArrayInputStream(content), metadata);
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

    public void deleteBlockLocal(KadKey key) {
        Regions clientRegion = this.region;
        String bucketName = this.bucket;
        String keyName = key.toString();

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
}
