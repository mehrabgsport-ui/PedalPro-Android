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

    static String toGregorianIso(String jalali){
        if(jalali==null||jalali.isBlank())return "";
        String x=en(jalali.trim()).replace('-','/').replace('.','/');
        String[] p=x.split("/");
        if(p.length<3)return "";
        try{
            int jy=Integer.parseInt(p[0].trim()),jm=Integer.parseInt(p[1].trim()),jd=Integer.parseInt(p[2].trim().split("\\s+")[0]);
            int[] g=toGregorian(jy,jm,jd);
            return String.format(java.util.Locale.US,"%04d-%02d-%02d",g[0],g[1],g[2]);
        }catch(Exception e){return "";}
    }

    static int[] toGregorian(int jy,int jm,int jd){
        jy+=1595;
        long days=-355668L+365L*jy+(jy/33)*8+((jy%33+3)/4)+jd+(jm<7?(jm-1)*31:(jm-7)*30+186);
        int gy=(int)(400*(days/146097));days%=146097;
        if(days>36524){gy+=(int)(100*(--days/36524));days%=36524;if(days>=365)days++;}
        gy+=(int)(4*(days/1461));days%=1461;
        if(days>365){gy+=(int)((days-1)/365);days=(days-1)%365;}
        int gd=(int)days+1;
        int[] sal={0,31,((gy%4==0&&gy%100!=0)||gy%400==0)?29:28,31,30,31,30,31,31,30,31,30,31};
        int gm=1;while(gm<=12&&gd>sal[gm]){gd-=sal[gm];gm++;}
        return new int[]{gy,gm,gd};
    }

    static String en(String value){
        if(value==null)return "";
        String s=value;
        char[] fa="۰۱۲۳۴۵۶۷۸۹".toCharArray(), ar="٠١٢٣٤٥٦٧٨٩".toCharArray(), en="0123456789".toCharArray();
        for(int i=0;i<10;i++){s=s.replace(fa[i],en[i]);s=s.replace(ar[i],en[i]);}
        return s;
    }

    static String fa(Object value){
        String s=String.valueOf(value);
        char[] en="0123456789".toCharArray(), fa="۰۱۲۳۴۵۶۷۸۹".toCharArray();
        for(int i=0;i<10;i++)s=s.replace(en[i],fa[i]);
        return s;
    }
    private static String twoFa(int n){return fa(String.format(java.util.Locale.US,"%02d",n));}
}
