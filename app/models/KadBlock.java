package models;

import io.ebean.*;
import services.kademlia.KadKey;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.List;

/**
 * Represents an stored chunk
 */
@Entity
public class KadBlock extends Model {

    @Id
    public String id;

    public long sizeBytes;

    private static Finder<String, KadBlock> find = new Finder<>(KadBlock.class);

    public KadBlock(String id, long sizeBytes) {
        this.id = id;
        this.sizeBytes = sizeBytes;
    }

    public KadKey getKadKey() {
        return KadKey.fromHex(this.id);
    }

    /**
     * Mark a block as stored.
     * @param id The block ID
     * @param sizeBytes The block size in bytes.
     */
    public static void store(String id, long sizeBytes) {
        remove(id);
        KadBlock b = new KadBlock(id, sizeBytes);
        try {
            b.save();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Removes a block form the database.
     * @param id The block id.
     */
    public static void remove(String id) {
        KadBlock b = find.byId(id);
        if (b != null) {
            try {
                b.delete();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * @return The size of the stored data.
     */
    public long computeTotalSize() {
        final String sql = "SELECT SUM(size_bytes) as total FROM kad_block";

        SqlQuery sqlQuery = Ebean.createSqlQuery(sql);
        SqlRow row = sqlQuery.findOne();
        return row.getLong("total");
    }

    public static int countAll() {
        return find.query().where().findCount();
    }

    public static List<KadBlock> getPage(int skip, int limit) {
        return find.query().where().setFirstRow(skip).setMaxRows(limit).findList();
    }
}
