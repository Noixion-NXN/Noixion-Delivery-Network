package services.kademlia;

import services.kademlia.Contact;
import services.kademlia.KademliaConfiguration;

import java.util.*;

/**
 * Represents a bucket in the Kademlia routing table.
 */
public class KBucket {
    private final int depth;

    private final TreeSet<Contact> contacts;

    private final TreeSet<Contact> replacementCache;

    public KBucket(int depth) {
        this.depth = depth;
        this.contacts = new TreeSet<>();
        this.replacementCache = new TreeSet<>();
    }

    /**
     * Insers a contact in the bucket.
     * @param c The contact.
     */
    public synchronized void insert(Contact c) {
        if (this.contacts.contains(c)) {
            Contact cont = this.removeFromContacts(c);
            if (cont.getLastSeen() < c.getLastSeen()) {
                cont.setLastSeen(c.getLastSeen());
                cont.resetStaleCount();
            }
            this.contacts.add(cont);
        } else {
            /* If the bucket is filled, so put the contacts in the replacement cache */
            if (contacts.size() >= KademliaConfiguration.K)
            {
                /* If the cache is empty, we check if any contacts are stale and replace the stalest one */
                Contact stalest = null;
                for (Contact tmp : this.contacts)
                {
                    if (tmp.getStaleCount() >= KademliaConfiguration.STALE)
                    {
                        /* Contact is stale */
                        if (stalest == null)
                        {
                            stalest = tmp;
                        }
                        else if (tmp.getStaleCount() > stalest.getStaleCount())
                        {
                            stalest = tmp;
                        }
                    }
                }

                /* If we have a stale contact, remove it and add the new contact to the bucket */
                if (stalest != null)
                {
                    this.contacts.remove(stalest);
                    this.contacts.add(c);
                }
                else
                {
                    /* No stale contact, lets insert this into replacement cache */
                    this.insertIntoReplacementCache(c);
                }
            }
            else
            {
                this.contacts.add(c);
            }
        }
    }

    /**
     * Finds a contact in the bucket contacts list.
     * @param c The contact
     * @return The found contact.
     */
    public synchronized Contact getFromContacts(Contact c) {
        for (Contact cont : this.contacts) {
            if (cont.equals(c)) {
                return cont;
            }
        }

        throw new NoSuchElementException("The contact does not exist in the contacts list.");
    }

    /**
     * Removes a contact from the bucket contacts list.
     * @param c The contact
     * @return The removed contact.
     */
    public synchronized Contact removeFromContacts(Contact c) {
        for (Contact cont : this.contacts) {
            if (cont.equals(c)) {
                this.contacts.remove(cont);
                return cont;
            }
        }

        throw new NoSuchElementException("The contact does not exist in the contacts list.");
    }

    /**
     * @return The number of contacts.
     */
    public synchronized int numContacts() {
        return this.contacts.size();
    }

    /**
     * @return A copy of the list of contacts.
     */
    public synchronized List<Contact> getContacts() {
        List<Contact> result = (new ArrayList<>());
        result.addAll(this.contacts);
        return result;
    }

    /**
     * Checks if the bucket contains a contact
     * @param c The contact
     * @return True if it contains the contact.
     */
    public synchronized boolean containsContact(Contact c)
    {
        return this.contacts.contains(c);
    }

    /**
     * Removes a contact if there is a replacement for it. If not, it increments the stale counter.
     * @param c The contact
     * @return True if success, false if failed
     */
    public synchronized boolean removeContact(Contact c)
    {
        /* If the contact does not exist, then we failed to remove it */
        if (!this.contacts.contains(c))
        {
            return false;
        }

        /* Contact exist, lets remove it only if our replacement cache has a replacement */
        if (!this.replacementCache.isEmpty())
        {
            /* Replace the contact with one from the replacement cache */
            this.contacts.remove(c);
            Contact replacement = this.replacementCache.first();
            this.contacts.add(replacement);
            this.replacementCache.remove(replacement);
        }
        else
        {
            /* There is no replacement, just increment the contact's stale count */
            this.getFromContacts(c).incrementStaleCount();
        }

        return true;
    }

    /**
     * Inserts a contact into the replacement cache.
     * @param c The contact.
     */
    private synchronized void insertIntoReplacementCache(Contact c)
    {
        /* Just return if this contact is already in our replacement cache */
        if (this.replacementCache.contains(c))
        {
            /**
             * If the contact is already in the bucket, lets update that we've seen it
             * We need to remove and re-add the contact to get the Sorted Set to update sort order
             */
            Contact tmp = this.removeFromReplacementCache(c);
            tmp.setSeenNow();
            this.replacementCache.add(tmp);
        }
        else if (this.replacementCache.size() > KademliaConfiguration.K)
        {
            /* if our cache is filled, we remove the least recently seen contact */
            this.replacementCache.remove(this.replacementCache.last());
            this.replacementCache.add(c);
        }
        else
        {
            this.replacementCache.add(c);
        }
    }

    /**
     * Removes a constact from the replacement cache.
     * @param c The contact.
     * @return The removed contact.
     */
    private synchronized Contact removeFromReplacementCache(Contact c)
    {
        for (Contact cont : this.replacementCache)
        {
            if (cont.equals(c))
            {
                this.replacementCache.remove(c);
                return c;
            }
        }

        /* We got here means this element does not exist */
        throw new NoSuchElementException("Node does not exist in the replacement cache. ");
    }


    public int getDepth() {
        return this.depth;
    }


    @Override
    public synchronized String toString()
    {
        StringBuilder sb = new StringBuilder("Bucket at depth: ");
        sb.append(this.depth);
        sb.append("\n Nodes: \n");
        for (Contact n : this.contacts)
        {
            sb.append("Node: ");
            sb.append(n.toString());
            sb.append(" (stale: ");
            sb.append(n.getStaleCount());
            sb.append(" / Last seen: ");
            sb.append(new Date(n.getLastSeen()).toString());
            sb.append(")");
            sb.append("\n");
        }

        return sb.toString();
    }

}
