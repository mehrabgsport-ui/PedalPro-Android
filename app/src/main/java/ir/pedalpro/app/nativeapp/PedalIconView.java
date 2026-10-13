package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight vector icon set used by the native PedalPro shell. */
public final class PedalIconView extends View {
    public enum Icon {
        HOME, CALENDAR, RIDE, ROUTE, USER, MENU, SETTINGS, GROUP, CHAT, CHART,
        SHIELD, SENSOR, BEACON, PLUS, REFRESH, BACK, PLAY, PAUSE, CLOCK,
        LOCATION, DISTANCE, CLOSE, EDIT, SHARE, DETAILS, SUN, CLOUD, RAIN, CHEVRON,
        GALLERY, HOME_MEMBERS, REPORT_PROGRAMS, REPORT_MEMBERS, REPORT_DISTANCE
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Icon icon = Icon.HOME;
    private int color = Color.WHITE;
    private float stroke = 2.25f;

    public PedalIconView(Context c) { super(c); init(); }
    public PedalIconView(Context c, AttributeSet a) { super(c,a); init(); }
    public PedalIconView(Context c, Icon icon, int color) { super(c); this.icon=icon; this.color=color; init(); }

    private void init(){ p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }

    public void setIcon(Icon value){ icon=value==null?Icon.HOME:value; invalidate(); }
    public void setColor(int value){ color=value; invalidate(); }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),s=Math.min(w,h),cx=w/2f,cy=h/2f;
        float r=s*.31f;
        p.setColor(color); p.setStrokeWidth(Math.max(2f,getResources().getDisplayMetrics().density*stroke)); p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch(icon){
            case HOME:
                path.moveTo(cx-r,cy); path.lineTo(cx,cy-r*.9f); path.lineTo(cx+r,cy);
                path.moveTo(cx-r*.72f,cy-r*.05f); path.lineTo(cx-r*.72f,cy+r*.8f); path.lineTo(cx+r*.72f,cy+r*.8f); path.lineTo(cx+r*.72f,cy-r*.05f);
                c.drawPath(path,p); break;
            case CALENDAR:
                c.drawRoundRect(cx-r,cy-r*.72f,cx+r,cy+r*.82f,r*.18f,r*.18f,p);
                c.drawLine(cx-r,cy-r*.26f,cx+r,cy-r*.26f,p);
                c.drawLine(cx-r*.48f,cy-r,cx-r*.48f,cy-r*.55f,p); c.drawLine(cx+r*.48f,cy-r,cx+r*.48f,cy-r*.55f,p);
                dot(c,cx-r*.45f,cy+r*.08f,s*.035f); dot(c,cx,cy+r*.08f,s*.035f); dot(c,cx+r*.45f,cy+r*.08f,s*.035f);
                dot(c,cx-r*.45f,cy+r*.43f,s*.035f); dot(c,cx,cy+r*.43f,s*.035f); break;
            case RIDE:
                c.drawCircle(cx-r*.62f,cy+r*.35f,r*.38f,p); c.drawCircle(cx+r*.65f,cy+r*.35f,r*.38f,p);
                path.moveTo(cx-r*.62f,cy+r*.35f); path.lineTo(cx-r*.08f,cy-r*.15f); path.lineTo(cx+r*.2f,cy+r*.35f); path.close(); c.drawPath(path,p);
                c.drawLine(cx-r*.08f,cy-r*.15f,cx+r*.55f,cy-r*.18f,p); c.drawLine(cx+r*.2f,cy+r*.35f,cx+r*.55f,cy-r*.18f,p);
                c.drawLine(cx-r*.18f,cy-r*.38f,cx+r*.05f,cy-r*.38f,p); break;
            case ROUTE:
            case LOCATION:
                path.moveTo(cx,cy+r); path.cubicTo(cx-r*.95f,cy+r*.1f,cx-r*.85f,cy-r*.95f,cx,cy-r*.95f); path.cubicTo(cx+r*.85f,cy-r*.95f,cx+r*.95f,cy+r*.1f,cx,cy+r); c.drawPath(path,p); c.drawCircle(cx,cy-r*.25f,r*.27f,p); break;
            case USER:
                c.drawCircle(cx,cy-r*.48f,r*.32f,p); c.drawArc(new RectF(cx-r*.8f,cy-r*.05f,cx+r*.8f,cy+r*1.25f),200,140,false,p); break;
            case MENU:
                c.drawLine(cx-r,cy-r*.55f,cx+r,cy-r*.55f,p); c.drawLine(cx-r,cy,cx+r,cy,p); c.drawLine(cx-r,cy+r*.55f,cx+r,cy+r*.55f,p); break;
            case SETTINGS:
                c.drawCircle(cx,cy,r*.34f,p); for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=cx+(float)Math.cos(a)*r*.62f,y1=cy+(float)Math.sin(a)*r*.62f,x2=cx+(float)Math.cos(a)*r,y2=cy+(float)Math.sin(a)*r;c.drawLine(x1,y1,x2,y2,p);} break;
            case GROUP:
                c.drawCircle(cx-r*.42f,cy-r*.35f,r*.28f,p); c.drawCircle(cx+r*.42f,cy-r*.35f,r*.28f,p);
                c.drawArc(new RectF(cx-r,cy-r*.05f,cx,cy+r*.9f),195,150,false,p); c.drawArc(new RectF(cx,cy-r*.05f,cx+r,cy+r*.9f),195,150,false,p); break;
            case CHAT:
                c.drawRoundRect(cx-r,cy-r*.75f,cx+r,cy+r*.55f,r*.22f,r*.22f,p); path.moveTo(cx+r*.25f,cy+r*.55f);path.lineTo(cx+r*.55f,cy+r);path.lineTo(cx-r*.05f,cy+r*.58f);c.drawPath(path,p); break;
            case CHART:
                c.drawLine(cx-r,cy+r*.8f,cx-r,cy-r*.8f,p); c.drawLine(cx-r,cy+r*.8f,cx+r,cy+r*.8f,p);
                path.moveTo(cx-r*.78f,cy+r*.38f);path.lineTo(cx-r*.25f,cy-r*.05f);path.lineTo(cx+r*.1f,cy+r*.15f);path.lineTo(cx+r*.75f,cy-r*.65f);c.drawPath(path,p); break;
            case SHIELD:
                path.moveTo(cx,cy-r);path.lineTo(cx+r*.82f,cy-r*.65f);path.lineTo(cx+r*.62f,cy+r*.35f);path.cubicTo(cx+r*.4f,cy+r*.8f,cx,cy+r,cx,cy+r);path.cubicTo(cx,cy+r,cx-r*.4f,cy+r*.8f,cx-r*.62f,cy+r*.35f);path.lineTo(cx-r*.82f,cy-r*.65f);path.close();c.drawPath(path,p);break;
            case SENSOR:
                path.moveTo(cx-r,cy);path.lineTo(cx-r*.65f,cy);path.lineTo(cx-r*.38f,cy-r*.65f);path.lineTo(cx-r*.05f,cy+r*.7f);path.lineTo(cx+r*.28f,cy-r*.72f);path.lineTo(cx+r*.55f,cy);path.lineTo(cx+r,cy);c.drawPath(path,p);break;
            case BEACON:
                c.drawCircle(cx,cy,r*.18f,p); c.drawArc(new RectF(cx-r*.55f,cy-r*.55f,cx+r*.55f,cy+r*.55f),-55,110,false,p);c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-55,110,false,p);c.drawArc(new RectF(cx-r*.55f,cy-r*.55f,cx+r*.55f,cy+r*.55f),125,110,false,p);c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),125,110,false,p);break;
            case PLUS:
                c.drawLine(cx-r*.7f,cy,cx+r*.7f,cy,p);c.drawLine(cx,cy-r*.7f,cx,cy+r*.7f,p);break;
            case REFRESH:
                c.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-60,280,false,p);path.moveTo(cx+r*.82f,cy-r*.65f);path.lineTo(cx+r*.95f,cy-r*.05f);path.lineTo(cx+r*.38f,cy-r*.2f);c.drawPath(path,p);break;
            case BACK:
            case CHEVRON:
                path.moveTo(cx+r*.35f,cy-r*.65f);path.lineTo(cx-r*.35f,cy);path.lineTo(cx+r*.35f,cy+r*.65f);c.drawPath(path,p);break;
            case PLAY:
                p.setStyle(Paint.Style.FILL);path.moveTo(cx-r*.55f,cy-r*.75f);path.lineTo(cx+r*.72f,cy);path.lineTo(cx-r*.55f,cy+r*.75f);path.close();c.drawPath(path,p);break;
            case PAUSE:
                p.setStyle(Paint.Style.FILL);c.drawRoundRect(cx-r*.6f,cy-r*.72f,cx-r*.15f,cy+r*.72f,r*.08f,r*.08f,p);c.drawRoundRect(cx+r*.15f,cy-r*.72f,cx+r*.6f,cy+r*.72f,r*.08f,r*.08f,p);break;
            case CLOCK:
                c.drawCircle(cx,cy,r,p);c.drawLine(cx,cy,cx,cy-r*.58f,p);c.drawLine(cx,cy,cx+r*.46f,cy+r*.22f,p);break;
            case DISTANCE:
                c.drawLine(cx-r,cy,cx+r,cy,p);path.moveTo(cx-r,cy);path.lineTo(cx-r*.55f,cy-r*.35f);path.moveTo(cx-r,cy);path.lineTo(cx-r*.55f,cy+r*.35f);path.moveTo(cx+r,cy);path.lineTo(cx+r*.55f,cy-r*.35f);path.moveTo(cx+r,cy);path.lineTo(cx+r*.55f,cy+r*.35f);c.drawPath(path,p);break;
            case CLOSE:
                c.drawLine(cx-r*.7f,cy-r*.7f,cx+r*.7f,cy+r*.7f,p);c.drawLine(cx+r*.7f,cy-r*.7f,cx-r*.7f,cy+r*.7f,p);break;
            case EDIT:
                c.drawLine(cx-r*.65f,cy+r*.55f,cx+r*.55f,cy-r*.65f,p);c.drawRect(cx-r*.75f,cy+r*.48f,cx-r*.42f,cy+r*.78f,p);break;
            case SHARE:
                c.drawCircle(cx-r*.68f,cy,r*.2f,p);c.drawCircle(cx+r*.62f,cy-r*.62f,r*.2f,p);c.drawCircle(cx+r*.62f,cy+r*.62f,r*.2f,p);c.drawLine(cx-r*.48f,cy-r*.06f,cx+r*.42f,cy-r*.53f,p);c.drawLine(cx-r*.48f,cy+r*.06f,cx+r*.42f,cy+r*.53f,p);break;
            case DETAILS:
                c.drawRoundRect(cx-r*.75f,cy-r,cx+r*.75f,cy+r,r*.12f,r*.12f,p);c.drawLine(cx-r*.45f,cy-r*.4f,cx+r*.45f,cy-r*.4f,p);c.drawLine(cx-r*.45f,cy,cx+r*.45f,cy,p);c.drawLine(cx-r*.45f,cy+r*.4f,cx+r*.18f,cy+r*.4f,p);break;
            case SUN:
                c.drawCircle(cx,cy,r*.42f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(cx+(float)Math.cos(a)*r*.62f,cy+(float)Math.sin(a)*r*.62f,cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,p);}break;
            case CLOUD:
                drawCloud(c,cx,cy,r);break;
            case RAIN:
                drawCloud(c,cx,cy-r*.12f,r*.9f);
                c.drawLine(cx-r*.55f,cy+r*.55f,cx-r*.7f,cy+r*.95f,p);
                c.drawLine(cx,cy+r*.55f,cx-r*.15f,cy+r*.95f,p);
                c.drawLine(cx+r*.55f,cy+r*.55f,cx+r*.4f,cy+r*.95f,p);break;
            case GALLERY: {
                float k=s/64f, ox=cx-s/2f, oy=cy-s/2f;
                c.drawRoundRect(ox+10*k,oy+14*k,ox+54*k,oy+48*k,5*k,5*k,p);
                path.moveTo(ox+16*k,oy+42*k); path.lineTo(ox+28*k,oy+30*k); path.lineTo(ox+37*k,oy+39*k); path.lineTo(ox+44*k,oy+32*k); path.lineTo(ox+48*k,oy+36*k); c.drawPath(path,p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(ox+22*k,oy+23*k,3.2f*k,p); p.setStyle(Paint.Style.STROKE);
                break;
            }
            case HOME_MEMBERS: {
                float k=s/64f, ox=cx-s/2f, oy=cy-s/2f;
                c.drawCircle(ox+32*k,oy+20*k,8*k,p);
                path.moveTo(ox+18*k,oy+46*k); path.cubicTo(ox+18*k,oy+38*k,ox+24*k,oy+33*k,ox+32*k,oy+33*k); path.cubicTo(ox+40*k,oy+33*k,ox+46*k,oy+38*k,ox+46*k,oy+46*k); c.drawPath(path,p);
                c.drawCircle(ox+18*k,oy+25*k,5.5f*k,p); c.drawCircle(ox+46*k,oy+25*k,5.5f*k,p);
                path.reset(); path.moveTo(ox+8*k,oy+46*k); path.cubicTo(ox+8*k,oy+40.5f*k,ox+12.5f*k,oy+36.5f*k,ox+18.5f*k,oy+36.5f*k);
                path.moveTo(ox+56*k,oy+46*k); path.cubicTo(ox+56*k,oy+40.5f*k,ox+51.5f*k,oy+36.5f*k,ox+45.5f*k,oy+36.5f*k); c.drawPath(path,p);
                break;
            }
            case REPORT_PROGRAMS: {
                float k=s/64f, ox=cx-s/2f, oy=cy-s/2f;
                c.drawCircle(ox+17*k,oy+42*k,6*k,p); c.drawCircle(ox+31*k,oy+21*k,6*k,p); c.drawCircle(ox+48*k,oy+39*k,6*k,p);
                c.drawLine(ox+21*k,oy+37*k,ox+28*k,oy+26*k,p); c.drawLine(ox+36*k,oy+24*k,ox+44*k,oy+34*k,p); c.drawLine(ox+23*k,oy+43*k,ox+42*k,oy+43*k,p);
                break;
            }
            case REPORT_MEMBERS: {
                float k=s/64f, ox=cx-s/2f, oy=cy-s/2f;
                c.drawCircle(ox+32*k,oy+21*k,8*k,p); c.drawCircle(ox+18*k,oy+26*k,6*k,p); c.drawCircle(ox+46*k,oy+26*k,6*k,p);
                path.moveTo(ox+20*k,oy+50*k); path.cubicTo(ox+20*k,oy+41*k,ox+25*k,oy+35*k,ox+32*k,oy+35*k); path.cubicTo(ox+39*k,oy+35*k,ox+44*k,oy+41*k,ox+44*k,oy+50*k);
                path.moveTo(ox+7*k,oy+49*k); path.cubicTo(ox+7*k,oy+42*k,ox+11*k,oy+37*k,ox+18*k,oy+37*k);
                path.moveTo(ox+57*k,oy+49*k); path.cubicTo(ox+57*k,oy+42*k,ox+53*k,oy+37*k,ox+46*k,oy+37*k); c.drawPath(path,p);
                break;
            }
            case REPORT_DISTANCE: {
                float k=s/64f, ox=cx-s/2f, oy=cy-s/2f;
                path.moveTo(ox+8*k,oy+49*k); path.lineTo(ox+25*k,oy+20*k); path.lineTo(ox+36*k,oy+38*k); path.lineTo(ox+44*k,oy+26*k); path.lineTo(ox+56*k,oy+49*k); path.close(); c.drawPath(path,p);
                c.drawCircle(ox+31*k,oy+40*k,3*k,p);
                break;
            }
        }
    }
    private void drawCloud(Canvas c,float cx,float cy,float r){
        path.reset();
        path.moveTo(cx-r*.78f,cy+r*.4f);
        path.cubicTo(cx-r,cy-r*.12f,cx-r*.42f,cy-r*.35f,cx-r*.14f,cy-r*.2f);
        path.cubicTo(cx+r*.05f,cy-r*.88f,cx+r*.72f,cy-r*.72f,cx+r*.72f,cy-r*.22f);
        path.cubicTo(cx+r*1.1f,cy-r*.1f,cx+r,cy+r*.45f,cx+r*.58f,cy+r*.45f);
        path.lineTo(cx-r*.78f,cy+r*.4f);
        c.drawPath(path,p);
    }
    private void dot(Canvas c,float x,float y,float r){Paint.Style old=p.getStyle();p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setStyle(old);}
}
