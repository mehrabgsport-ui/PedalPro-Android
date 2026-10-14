package ir.pedalpro.app.nativeapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Compact vector social service glyphs. No emoji fonts or bitmap dependencies. */
public final class SocialNetworkIconView extends View {
    private final String provider;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int tint;
    public SocialNetworkIconView(Context context,String provider,int tint){
        super(context);this.provider=provider==null?"":provider.toLowerCase(java.util.Locale.ROOT);
        this.tint=tint;setContentDescription(provider);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int save=c.save();float side=Math.min(getWidth(),getHeight());
        c.translate((getWidth()-side)/2f,(getHeight()-side)/2f);c.scale(side/40f,side/40f);
        paint.setColor(tint);paint.setStrokeWidth(2.5f);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
        if(provider.contains("instagram")){
            c.drawRoundRect(new RectF(6,6,34,34),8,8,paint);
            c.drawCircle(20,20,7,paint);
            paint.setStyle(Paint.Style.FILL);c.drawCircle(29,11,2,paint);
        } else if(provider.contains("telegram")){
            Path paper=new Path();paper.moveTo(5,18);paper.lineTo(35,7);
            paper.lineTo(27,34);paper.lineTo(19,25);paper.close();
            paint.setStyle(Paint.Style.FILL);c.drawPath(paper,paint);
            paint.setColor(0xFF0C2D42);paint.setStrokeWidth(2.5f);
            c.drawLine(19,25,31,13,paint);
        } else if(provider.contains("strava")){
            Path p=new Path();p.moveTo(19,3);p.lineTo(31,27);p.lineTo(24,27);
            p.lineTo(19,17);p.lineTo(14,27);p.lineTo(7,27);p.close();
            paint.setStyle(Paint.Style.FILL);c.drawPath(p,paint);
            p.reset();p.moveTo(18,29);p.lineTo(23,38);p.lineTo(28,29);p.lineTo(25,29);
            p.lineTo(23,33);p.lineTo(21,29);p.close();c.drawPath(p,paint);
        } else if(provider.contains("relive")){
            c.drawCircle(20,20,14,paint);
            Path play=new Path();play.moveTo(17,12);play.lineTo(28,20);
            play.lineTo(17,28);play.close();paint.setStyle(Paint.Style.FILL);
            c.drawPath(play,paint);
        } else if(provider.contains("ایتا")||provider.contains("eitaa")){
            Path speech=new Path();speech.moveTo(9,8);speech.lineTo(31,8);
            speech.quadTo(35,8,35,14);speech.lineTo(35,27);
            speech.quadTo(35,31,29,31);speech.lineTo(17,31);
            speech.lineTo(9,37);speech.lineTo(9,31);speech.quadTo(5,31,5,26);
            speech.lineTo(5,14);speech.quadTo(5,8,9,8);speech.close();
            c.drawPath(speech,paint);paint.setStyle(Paint.Style.FILL);
            c.drawCircle(14,20,2,paint);c.drawCircle(20,20,2,paint);c.drawCircle(26,20,2,paint);
        } else {
            Path bubble=new Path();bubble.moveTo(8,8);bubble.lineTo(32,8);
            bubble.quadTo(36,8,36,13);bubble.lineTo(36,28);
            bubble.quadTo(36,33,31,33);bubble.lineTo(16,33);
            bubble.lineTo(8,38);bubble.lineTo(8,33);bubble.quadTo(4,32,4,28);
            bubble.lineTo(4,13);bubble.quadTo(4,8,8,8);bubble.close();
            c.drawPath(bubble,paint);
            paint.setStyle(Paint.Style.FILL);c.drawCircle(13,20,2,paint);
            c.drawCircle(20,20,2,paint);c.drawCircle(27,20,2,paint);
        }
        c.restoreToCount(save);
    }
}
