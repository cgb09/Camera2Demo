package com.example.cameracapturedemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
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

        mInnerRadiusPX = DensityUtils.dip2px(context,mInnerRadiusDP);
        mOuterRadiusPX = DensityUtils.dip2px(context,mOuterRadiusDP);
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
}
