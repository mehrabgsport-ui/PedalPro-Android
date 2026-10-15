package ir.pedalpro.app.nativeapp;
/**
 * Display-only speed filter: rejects isolated zero readings caused by GNSS
 * point quarantine. Never changes accepted track points or stored statistics.
 */
public final class RideSpeedDisplayFilter {
    private double speedKmh;
    private long lastReliableMs;
    private long firstInvalidMs;
    private boolean initialized;
    public synchronized double update(double rawKmh, boolean accepted,long monotonicMs,boolean paused) {
        if(paused){reset();return 0.0;}
        if(accepted&&Double.isFinite(rawKmh)&&rawKmh>=0.0&&rawKmh<=80.0){
            if(rawKmh>=1.3){
                if(!initialized){speedKmh=rawKmh;initialized=true;}
                else speedKmh=speedKmh*.68+rawKmh*.32;
                lastReliableMs=monotonicMs;
                firstInvalidMs=0;
                return speedKmh;
            }
            // A single accepted near-zero fix is not proof of stopping.
        }
        if(firstInvalidMs==0L)firstInvalidMs=monotonicMs;
        long elapsed=monotonicMs-Math.max(lastReliableMs,firstInvalidMs);
        if(lastReliableMs==0 || elapsed>=7000L){speedKmh=0;initialized=false;return 0;}
        if(elapsed>4500L)speedKmh=Math.max(0,speedKmh*.65);
        return speedKmh;
    }
    public synchronized double current(long now,boolean paused){
        if(paused)return 0;
        if(lastReliableMs>0 && now-lastReliableMs>8000L){reset();return 0;}
        return speedKmh;
    }
    public synchronized void reset(){
        speedKmh=0;lastReliableMs=0;firstInvalidMs=0;initialized=false;
    }
}
