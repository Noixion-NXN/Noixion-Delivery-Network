package services;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import play.db.Database;
import play.db.NamedDatabase;
import services.kademlia.BlockNotFoundException;
import services.kademlia.KadKey;
import utils.IPFSMapEntry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.SQLException;


/**
 * IPFS storage service
 *
 * Stores and reads files from IPFS file system
 */
@Singleton
public class IPFSStorageService {

    private IPFS ipfs;
    private Database mapDB;

    @Inject
    public IPFSStorageService(@NamedDatabase("ipfs") Database db, StorageModeService storageModeService) {
        Config config = ConfigFactory.load();

        if (!storageModeService.isIPFS()) {
            return;
        }

        this.ipfs = new IPFS(config.getString("ipfs.address"));

        this.mapDB = db;
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

        Multihash filePointer = Multihash.fromBase58(entry.getFile());

        byte[] fileContents;

        try {
            fileContents = entry.decrypt(ipfs.cat(filePointer));
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

        NamedStreamable.ByteArrayWrapper baw = new NamedStreamable.ByteArrayWrapper(cc);


        MerkleNode addResult = ipfs.add(baw).get(0);

        entry.setFile(addResult.hash.toBase58());

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
