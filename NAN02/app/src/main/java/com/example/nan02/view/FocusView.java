package com.example.nan02.view;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class FocusView extends View {
    private Paint mPaint;
    private float mStrokeWidth = 4.0f;
    private int mInnerRadiusDP = 7;
    public static final int mOuterRadiusDP = 40;
    private int mInnerRadiusPX;
    private int mOuterRadiusPX;
    private float mViewCenterX;
    private float mViewCenterY;
    private boolean mNeedToDrawView;

    public FocusView(Context context) {
        this(context, null);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initData(context);
    }

    private void initData(Context context) {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        mInnerRadiusPX = dip2px(context,mInnerRadiusDP);
        mOuterRadiusPX = dip2px(context,mOuterRadiusDP);
    }
    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mNeedToDrawView){
            canvas.drawCircle(mViewCenterX, mViewCenterY, mOuterRadiusPX, mPaint);
            canvas.drawCircle(mViewCenterX, mViewCenterY, mInnerRadiusPX, mPaint);
        }
    }

   public void setFocusViewCenter(float x, float y) {
        mViewCenterX = x;
        mViewCenterY = y;
        invalidate();
    }

    public void setNeedToDrawView(boolean b) {
        mNeedToDrawView = b;
        invalidate();
    }

    public void playAnimation() {
        ValueAnimator animIner = ValueAnimator.ofFloat(mInnerRadiusPX, mInnerRadiusPX-5, mInnerRadiusPX);
        animIner.setDuration(500);
        animIner.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float currentValue = (float) animation.getAnimatedValue();
                mInnerRadiusPX = (int) currentValue;
                invalidate();
            }
        });

        ValueAnimator animOuter = ValueAnimator.ofFloat(mOuterRadiusPX, mOuterRadiusPX+10, mOuterRadiusPX);
        animOuter.setDuration(500);
        animOuter.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float currentValue = (float) animation.getAnimatedValue();
                mOuterRadiusPX = (int) currentValue;
                invalidate();
            }
        });
        AnimatorSet set =new AnimatorSet();
        set.playTogether(animIner,animOuter);
        set.start();
    }
}