package ir.pedalpro.app.nativeapp;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Persian calendar and Iran-local clock selector; ISO output is for existing PHP contracts. */
public final class NativeJalaliDateTimePicker {
    public interface OnSelect { void selected(String serverIso,String jalaliText); }
    private static final String[] MONTHS={"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
            "مهر","آبان","آذر","دی","بهمن","اسفند"};
    private static final String FORMAT="yyyy-MM-dd HH:mm:ss";
    private NativeJalaliDateTimePicker(){}

    public static void show(Context context,String original,boolean withTime,OnSelect action){
        LocalDateTime current=LocalDateTime.now(ZoneId.of("Asia/Tehran"));
        String raw=JalaliDate.en(original==null?"":original.trim());
        try{
            if(raw.matches("\\d{4}[/]\\d{1,2}[/]\\d{1,2}.*")){
                String d=JalaliDate.toGregorianIso(raw.substring(0,raw.indexOf(' ' )>0?raw.indexOf(' '):raw.length()));
                if(!d.isBlank())current=LocalDate.parse(d).atTime(current.toLocalTime());
            }else if(raw.length()>=10){
                String normalized=raw.replace('T',' ');
                current=normalized.length()>=16?LocalDateTime.parse(
                    normalized.substring(0,16),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    :LocalDate.parse(normalized.substring(0,10)).atTime(current.toLocalTime());
            }
        }catch(Exception ignored){}
        int[] j=JalaliDate.toJalali(current.getYear(),current.getMonthValue(),current.getDayOfMonth());
        LinearLayout root=new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,12,12,12);
        LinearLayout date=new LinearLayout(context);date.setGravity(Gravity.CENTER);
        NumberPicker year=number(context,1300,1500,j[0]);
        NumberPicker month=number(context,1,12,j[1]);
        month.setDisplayedValues(MONTHS);
        NumberPicker day=number(context,1,31,j[2]);
        date.addView(day,weight());date.addView(month,new LinearLayout.LayoutParams(0,-2,1.55f));
        date.addView(year,weight());root.addView(date);
        NumberPicker hour=number(context,0,23,current.getHour());
        NumberPicker minute=number(context,0,59,current.getMinute());
        if(withTime){
            LinearLayout clock=new LinearLayout(context);clock.setGravity(Gravity.CENTER);
            clock.addView(minute,weight());clock.addView(hour,weight());root.addView(clock);
        }
        AlertDialog dialog=new AlertDialog.Builder(context)
                .setTitle(withTime?"تاریخ شمسی و ساعت ایران":"انتخاب تاریخ شمسی")
                .setView(root).setNegativeButton("انصراف",null)
                .setPositiveButton("تأیید",(d,w)->{}).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                int[] g=JalaliDate.toGregorian(year.getValue(),month.getValue(),day.getValue());
                LocalDate chosen=LocalDate.of(g[0],g[1],g[2]);
                int[] check=JalaliDate.toJalali(g[0],g[1],g[2]);
                if(check[0]!=year.getValue()||check[1]!=month.getValue()||check[2]!=day.getValue())
                    throw new IllegalArgumentException("Invalid Jalali date");
                LocalDateTime selected=chosen.atTime(withTime?hour.getValue():0,withTime?minute.getValue():0);
                String iso=withTime?selected.format(DateTimeFormatter.ofPattern(FORMAT))
                        :chosen.format(DateTimeFormatter.ISO_LOCAL_DATE);
                String jalali=JalaliDate.fa(String.format(Locale.US,"%04d/%02d/%02d",
                        year.getValue(),month.getValue(),day.getValue()));
                if(withTime)jalali+=" · "+JalaliDate.fa(String.format(Locale.US,"%02d:%02d",hour.getValue(),minute.getValue()));
                action.selected(iso,jalali);
                dialog.dismiss();
            }catch(Exception failure){
                Toast.makeText(context,"تاریخ شمسی انتخاب‌شده معتبر نیست.",Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }
    private static NumberPicker number(Context c,int min,int max,int selected){
        NumberPicker p=new NumberPicker(c);p.setMinValue(min);p.setMaxValue(max);
        p.setValue(Math.max(min,Math.min(max,selected)));
        p.setWrapSelectorWheel(true);p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        return p;
    }
    private static LinearLayout.LayoutParams weight(){
        return new LinearLayout.LayoutParams(0,-2,1f);
    }
}
