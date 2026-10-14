package ir.pedalpro.app;
public final class GpsTrackQualityTest {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        check(GpsTrackQuality.stableFirstPair(35.7,51.4,35.700024,51.400017,7f,8f,1200L,0),"good lock");
        check(!GpsTrackQuality.stableFirstPair(35.7,51.4,35.7006,51.4,7f,8f,1200L,0),"teleport");
        check(!GpsTrackQuality.stableFirstPair(35.7,51.4,35.7,51.4,32f,7f,1200L,0),"coarse");
        check(!GpsTrackQuality.stableFirstPair(35.7,51.4,35.7,51.4,7f,7f,150L,0),"duplicate");
        check(GpsTrackQuality.stationaryJitter(5,3,8f,9f,0.65,true),"still wander");
        check(GpsTrackQuality.stationaryJitter(8,6,11f,10f,1.2,false),"weak drift");
        check(!GpsTrackQuality.stationaryJitter(26,35,6f,6f,3.5,true),"real ride");
        check(!GpsTrackQuality.stationaryJitter(16,27,6f,6f,2,true),"slow ride");
        System.out.println("PASS GPS geometry contract");
    }
}
