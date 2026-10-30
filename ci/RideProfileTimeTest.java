package ir.pedalpro.app.nativeapp;

public final class RideProfileTimeTest {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        double seconds=RideProfileTime.resolve(1_792_000_000L,"");
        double millis=RideProfileTime.resolve(1_792_000_000_000L,"");
        check(seconds==millis,"Seconds and milliseconds must normalize identically");
        check(Double.isNaN(RideProfileTime.resolve(0L,null)),"Unknown time must stay missing");
        double parsed=RideProfileTime.resolve(0L,"2026-10-15 10:00:00");
        check(Double.isFinite(parsed),"Iran-local timestamp should parse");
        check(Double.isFinite(RideProfileTime.resolve(0L,"2026-10-15T10:00:00Z")),
                "ISO-like time should parse");
        check(Double.isNaN(RideProfileTime.resolve(0L,"not a timestamp yet")),
                "Invalid date must not be interpreted as elapsed time");
        check("00:00".equals(RideProfileTime.formatElapsed(0)),"Zero clock");
        check("01:05".equals(RideProfileTime.formatElapsed(65)),"Minute clock");
        check("1:01:01".equals(RideProfileTime.formatElapsed(3661)),"Hour clock");
        check("—".equals(RideProfileTime.formatElapsed(Double.NaN)),"Missing duration");
        System.out.println("RideProfileTimeTest OK");
    }
}
