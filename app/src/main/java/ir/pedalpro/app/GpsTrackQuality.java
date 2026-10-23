package ir.pedalpro.app;

/** Pure geometry guards used by GPS tracking. */
public final class GpsTrackQuality {
    private GpsTrackQuality(){}
    public static double distanceMeters(double a,double b,double c,double d) {
        double dLat=Math.toRadians(c-a),dLng=Math.toRadians(d-b);
        double h=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(a))*
                Math.cos(Math.toRadians(c))*Math.sin(dLng/2)*Math.sin(dLng/2);
        return 12742017.6*Math.atan2(Math.sqrt(Math.max(0,h)),Math.sqrt(Math.max(0,1-h)));
    }
    public static boolean stableFirstPair(double aLat,double aLng,double bLat,double bLng,
                                          float accuracyA,float accuracyB,long dtMs,double speedMps) {
        if(dtMs<750L||dtMs>7000L||accuracyA<=0||accuracyB<=0||
                accuracyA>18f||accuracyB>18f)return false;
        double allowed=Math.max(18.0,Math.max(accuracyA,accuracyB)*1.6)+
                Math.max(0,speedMps)*dtMs/1000.0;
        return distanceMeters(aLat,aLng,bLat,bLng)<=allowed;
    }
    public static boolean stationaryJitter(double step,double net,float accuracyA,float accuracyB,
                                           double speedMps,boolean forwardSupport) {
        double radius=Math.max(7.0,Math.min(22.0,Math.max(accuracyA,accuracyB)*1.15));
        return (step<=radius && net<=radius*1.35 &&
                (speedMps<1.8 || !forwardSupport)) ||
                (net<=radius && step<=radius*1.2 && speedMps<1.3);
    }
}
