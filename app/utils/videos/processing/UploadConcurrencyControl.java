package utils.videos.processing;

import java.util.Map;
import java.util.TreeMap;

public class UploadConcurrencyControl {
    private static UploadConcurrencyControl instance = null;

    public synchronized static UploadConcurrencyControl getInstance() {
        if (instance == null) {
            instance = new UploadConcurrencyControl();
        }
        return instance;
    }

    private final Map<Long, UsageCounter> monitors;

    public UploadConcurrencyControl() {
        monitors = new TreeMap<>();
    }

    public synchronized Object getCriticalSection(Long id) {
        UsageCounter uc;
        if (!monitors.containsKey(id)) {
            uc = new UsageCounter();
            monitors.put(id, uc);
        } else {
            uc = monitors.get(id);
        }
        uc.incrementCount();
        return uc;
    }

    public synchronized void leftCriticalSection(Long id) {
        UsageCounter uc;
        if (!monitors.containsKey(id)) {
            return;
        } else {
            uc = monitors.get(id);
        }
        uc.decrementCount();
        if (uc.getCount() <= 0) {
            monitors.remove(id);
        }
    }
}
