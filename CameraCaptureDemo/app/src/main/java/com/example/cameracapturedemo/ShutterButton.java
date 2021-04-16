package com.example.cameracapturedemo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.telephony.SubscriptionManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ShutterButton extends View {
    private Paint mInnerFillCircle;
    private Paint mOuterRing;
    private int mWidth;
    private int mHeight;
    private int mRadius;
    private int mCurrentMode = CameraConstant.PHOTO_MODE;
    private boolean mVideoRecordState;
    private RectF mRectVideoRecording;

    public ShutterButton(Context context) {
        this(context, null);
    }


    public ShutterButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mInnerFillCircle = new Paint();
        mInnerFillCircle.setColor(getResources().getColor(R.color.circle_color_thumbnail));
        mInnerFillCircle.setAntiAlias(true);
        mInnerFillCircle.setStyle(Paint.Style.FILL);

        mOuterRing = new Paint();
        mOuterRing.setColor(getResources().getColor(R.color.circle_color_thumbnail));
        mOuterRing.setAntiAlias(true);
        mOuterRing.setStyle(Paint.Style.STROKE);
        mOuterRing.setStrokeWidth(3.0f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        mRadius = mWidth / 2 - 20;
        mRectVideoRecording = new RectF();
        mRectVideoRecording.left = mWidth / 4;
        mRectVideoRecording.right = mWidth * 3 / 4;
        mRectVideoRecording.top = mHeight / 4;
        mRectVideoRecording.bottom = mHeight * 3 / 4;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCurrentMode == CameraConstant.VIDEO_MODE) {
            mInnerFillCircle.setColor(Color.RED);
        } else if (mCurrentMode == CameraConstant.PHOTO_MODE) {
            mInnerFillCircle.setColor(Color.WHITE);
        }

        if (mVideoRecordState && mCurrentMode == CameraConstant.VIDEO_MODE) {
            canvas.drawRoundRect(mRectVideoRecording, 10.0f, 10.0f, mInnerFillCircle);
        } else {
            canvas.drawCircle(mWidth / 2, mHeight / 2, mRadius, mInnerFillCircle);
        }
        canvas.drawCircle(mWidth / 2, mHeight / 2, mWidth / 2 - 3, mOuterRing);
    }

    public void startPictureAnimator() {
        final ValueAnimator valueAnimator = ValueAnimator.ofFloat(mWidth / 2 - 20, mWidth / 2 - 10, mWidth / 2 - 20);
        valueAnimator.setDuration(800);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                mRadius = (int) value;
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {

            }
        });
        valueAnimator.start();
    }

    public void setCurrentMode(int currentMode) {
        mCurrentMode = currentMode;
        invalidate();
    }

    public OnShutterButtonClickLister mLister;

    public void setVideoRecordingState(boolean recording) {
        mVideoRecordState = recording;
        invalidate();
    }

    public interface OnShutterButtonClickLister {
        void onShutterButtonClick(int mode);
    }

    public void setOnShutterButtonClickListener(OnShutterButtonClickLister listener) {
        mLister = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                if (mLister != null) {
                    mLister.onShutterButtonClick(mCurrentMode);
                }
                break;
        }
        return true;
    }
}
