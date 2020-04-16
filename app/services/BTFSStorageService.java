package services;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.h2.util.IOUtils;
import play.db.Database;
import play.db.NamedDatabase;
import play.libs.Json;
import services.kademlia.BlockNotFoundException;
import services.kademlia.KadKey;
import utils.IPFSMapEntry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;


/**
 * IPFS storage service
 *
 * Stores and reads files from IPFS file system
 */
@Singleton
public class BTFSStorageService {

    private final URI baseURI;
    private final URI baseURIGW;
    private final Database mapDB;

    @Inject
    public BTFSStorageService(@NamedDatabase("ipfs") Database db, StorageModeService storageModeService) throws URISyntaxException {
        Config config = ConfigFactory.load();

        this.mapDB = db;

        if (!storageModeService.isBTFS()) {
            baseURI = null;
            baseURIGW = null;
            return;
        }

        this.baseURI = new URI(config.getString("btfs.api.server"));
        this.baseURIGW = new URI(config.getString("btfs.gateway.server"));
    }

    private URL getEndPoint(String path) {
        try {
            return this.baseURI.resolve(path).toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private URL getEndPointGateway(String path) {
        try {
            return this.baseURIGW.resolve(path).toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }


    public byte[] read(KadKey key) throws BlockNotFoundException, IOException {
        IPFSMapEntry entry = new IPFSMapEntry();

        try {
            entry.query(this.mapDB, key);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BlockNotFoundException();
        }

        if (entry.getFile() == null || entry.getFile().length() == 0) {
            throw new BlockNotFoundException();
        }

        String hash = entry.getFile();

        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpGet request = new HttpGet(this.getEndPointGateway("/btfs/" + URLEncoder.encode(hash, "UTF-8")).toString());

        InputStream is = httpclient.execute(request).getEntity().getContent();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        byte[] byteArray = buffer.toByteArray();


        byte[] fileContents;

        try {
            fileContents = entry.decrypt(byteArray);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new BlockNotFoundException();
        }

        return fileContents;
    }

    public void store(KadKey key, byte[] content) throws IOException {
        //System.out.println("Storing chunk " + key.toString() + " into IPFS");
        IPFSMapEntry entry = new IPFSMapEntry();

        entry.setHash(key);

        byte[] cc;
        try {
            cc = entry.encrypt(content);
        } catch (Exception e) {
            e.printStackTrace();;
            throw new IOException(e.getMessage());
        }


        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpPost request = new HttpPost(this.getEndPoint("/api/v0/add").toString());

        InputStreamBody body = new InputStreamBody(new ByteArrayInputStream(cc), ContentType.APPLICATION_OCTET_STREAM);

        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("file", body)
                .build();

        request.setEntity(reqEntity);

        InputStream is = httpclient.execute(request).getEntity().getContent();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        byte[] byteArray =buffer.toByteArray();



        String str = new String(byteArray);

        System.out.println("Store response: " + str);

        JsonNode node = Json.parse(str);

        entry.setFile(node.get("Hash").asText());

        try {
            entry.store(this.mapDB);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e.getMessage());
        }

       // System.out.println("Stored chunk " + key.toString() + " into IPFS as " + entry.getFile());
    }

    public void deleteBlockLocal(KadKey key) {
        IPFSMapEntry entry = new IPFSMapEntry();

        entry.setHash(key);

        try {
            entry.delete(this.mapDB);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
