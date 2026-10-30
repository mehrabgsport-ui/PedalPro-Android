package ir.pedalpro.app.nativeapp;
public final class RideSpeedDisplayFilterTest {
  private static void ok(boolean v,String name){if(!v)throw new AssertionError(name);}
  public static void main(String[] args) {
    RideSpeedDisplayFilter f=new RideSpeedDisplayFilter();
    double first=f.update(16.2,true,1000,false);
    ok(first>16&&first<17,"initial");
    double rejected=f.update(0,false,2000,false);
    ok(rejected>15,"rejected zero must not flicker");
    double next=f.update(15.8,true,3000,false);
    ok(next>15&&next<17,"smooth cruise");
    ok(f.update(0,false,4000,false)>14,"momentary GNSS loss");
    ok(f.update(0,false,11000,false)==0,"long stationary interval");
    ok(f.update(12,true,12000,false)==12,"restart after stop");
    ok(f.update(12,true,13000,true)==0,"pause immediately zero");
    System.out.println("PASS live speed display stability / stop / pause");
  }
}
