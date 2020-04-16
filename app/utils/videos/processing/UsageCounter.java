package utils.videos.processing;

/**
 * Usage counter.
 */
public class UsageCounter {
    private int count;

    public UsageCounter() {
        count = 0;
    }

    public int getCount() {
        return count;
    }

    public int incrementCount() {
        count++;
        return count;
    }

    public int decrementCount() {
        count--;
        return count;
    }
}
