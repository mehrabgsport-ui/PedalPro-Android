package ir.pedalpro.app.nativeapp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class JalaliDate {
    private static final String[] MONTHS={"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"};
    private static final String[] WEEK={"دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه","شنبه","یکشنبه"};
    private JalaliDate(){}

    static String format(String raw, boolean withTime){
        if(raw==null||raw.isBlank())return "—";
        try{
            String clean=raw.trim().replace('T',' ');
            if(clean.endsWith("Z"))clean=clean.substring(0,clean.length()-1);
            LocalDateTime dt;
            if(clean.length()>=19)dt=LocalDateTime.parse(clean.substring(0,19),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            else if(clean.length()>=16)dt=LocalDateTime.parse(clean.substring(0,16),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            else dt=LocalDate.parse(clean.substring(0,10)).atStartOfDay();
            int[] j=toJalali(dt.getYear(),dt.getMonthValue(),dt.getDayOfMonth());
            String base=fa(j[2])+" "+MONTHS[j[1]-1]+" "+fa(j[0]);
            return withTime?base+" · "+twoFa(dt.getHour())+":"+twoFa(dt.getMinute()):base;
        }catch(Exception e){return raw;}
    }

    static String weekdayAndDate(String raw){
        if(raw==null||raw.isBlank())return "—";
        try{
            LocalDate d=LocalDate.parse(raw.substring(0,10));
            int[] j=toJalali(d.getYear(),d.getMonthValue(),d.getDayOfMonth());
            return WEEK[d.getDayOfWeek().getValue()-1]+" "+fa(j[2])+" "+MONTHS[j[1]-1];
        }catch(Exception e){return raw;}
    }

    static String todayForInput(){
        LocalDate d=LocalDate.now();
        int[] j=toJalali(d.getYear(),d.getMonthValue(),d.getDayOfMonth());
        return fa(j[0])+"/"+twoFa(j[1])+"/"+twoFa(j[2]);
    }

    static int[] toJalali(int gy,int gm,int gd){
        int[] gdm={0,31,59,90,120,151,181,212,243,273,304,334};
        int gy2=gm>2?gy+1:gy;
        long days=355666L+365L*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400+gd+gdm[gm-1];
        int jy=(int)(-1595+33*(days/12053)); days%=12053;
        jy+=4*(days/1461); days%=1461;
        if(days>365){jy+=(days-1)/365;days=(days-1)%365;}
        int jm,jd;
        if(days<186){jm=1+(int)(days/31);jd=1+(int)(days%31);}
        else{jm=7+(int)((days-186)/30);jd=1+(int)((days-186)%30);}
        return new int[]{jy,jm,jd};
    }

    static String fa(Object value){
        String s=String.valueOf(value);
        char[] en="0123456789".toCharArray(), fa="۰۱۲۳۴۵۶۷۸۹".toCharArray();
        for(int i=0;i<10;i++)s=s.replace(en[i],fa[i]);
        return s;
    }
    private static String twoFa(int n){return fa(String.format(java.util.Locale.US,"%02d",n));}
}
