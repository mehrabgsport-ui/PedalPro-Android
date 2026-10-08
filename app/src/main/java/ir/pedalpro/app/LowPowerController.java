package ir.pedalpro.app;

public class LowPowerController {
    private boolean paused;
    private double lastDistance;
    private long lastElapsed;

    public void pause(double distance, long elapsed) {
        paused = true;
        lastDistance = distance;
        lastElapsed = elapsed;
    }

    public void resume() {
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public double getDistance(double currentDistance) {
        return paused ? lastDistance : currentDistance;
    }

    public long getElapsed(long currentElapsed) {
        return paused ? lastElapsed : currentElapsed;
    }

    public float getSpeed(float currentSpeed) {
        return paused ? 0f : currentSpeed;
    }
}
