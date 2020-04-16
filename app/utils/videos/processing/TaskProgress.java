package utils.videos.processing;

public class TaskProgress {
    private final String name;
    private double progress;

    public TaskProgress(String name) {
        this.name = name;
        this.setProgress(0.0);
    }

    public String getName() {
        return name;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }
}
