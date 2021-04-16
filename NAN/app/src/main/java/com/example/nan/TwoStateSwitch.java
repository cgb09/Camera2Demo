package com.example.nan;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TwoStateSwitch extends FrameLayout {
    private ImageView ivCheckOn, ivCheckOff;// 两种状态的ImageView
    private CustomCheckBoxChangeListener customCheckBoxChangeListener;// 切换的监听器

    private boolean isCheck;// 是否被选中的标志值
    public TwoStateSwitch(@NonNull Context context) {
        this(context, null);
    }

    public TwoStateSwitch(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // 设置布局文件
        LayoutInflater.from(context).inflate(R.layout.flash_switch, this);

        // 获取控件元素
        ivCheckOn = (ImageView) findViewById(R.id.view_flash_on);
        ivCheckOff = (ImageView) findViewById(R.id.view_flash_off);

        // 设置两个ImageView的点击事件
        ivCheckOn.setOnClickListener(new ClickListener());
        ivCheckOff.setOnClickListener(new ClickListener());

        // 读取xml中设置的资源属性ID
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.TwoStateSwitch);
        int imageOnResId = array.getResourceId(R.styleable.TwoStateSwitch_imageOn, -1);
        int imageOffResId = array.getResourceId(R.styleable.TwoStateSwitch_imageOff, -1);

        // 设置显示资源
        setOnImage(imageOnResId);
        setOffImage(imageOffResId);

        // 对象回收
        array.recycle();

        // 默认显示的是没被选中的状态
        setCheckOff();
    }

    /**
     * 为CustomCheckBox设置监听器
     *
     * @param customCheckBoxChangeListener
     *            监听器接口对象
     */
    public void setCustomCheckBoxChangeListener(
            CustomCheckBoxChangeListener customCheckBoxChangeListener) {
        this.customCheckBoxChangeListener = customCheckBoxChangeListener;
    }

    /**
     * 设置开启状态时CustomCheckBox的图片
     *
     * @param resId
     *            图片资源ID
     */
    public void setOnImage(int resId) {
        ivCheckOn.setImageResource(resId);
    }

    /**
     * 设置关闭状态时CustomCheckBox的图片
     *
     * @param resId
     *            图片资源ID
     */
    public void setOffImage(int resId) {
        ivCheckOff.setImageResource(resId);
    }

    /**
     * 设置CustomCheckBox为关闭状态
     */
    public void setCheckOff() {
        isCheck = false;
        ivCheckOn.setVisibility(GONE);
        ivCheckOff.setVisibility(VISIBLE);
    }

    /**
     * 设置CustomCheckBox为开启状态
     */
    public void setCheckOn() {
        isCheck = true;
        ivCheckOn.setVisibility(VISIBLE);
        ivCheckOff.setVisibility(GONE);
    }

    /**
     * 获取CustomCheckBox的选择状态
     *
     * @return true CustomCheckBox已被选择
     * @return false CustomCheckBox未被选择
     */
    public boolean isCheck() {
        return isCheck;
    }

    /**
     * 状态改变监听接口
     */
    public interface CustomCheckBoxChangeListener {
        void customCheckBoxOn(int flashSwitch);

        void customCheckBoxOff(int flashSwitch);
    }

    /**
     * 自定义CustomCheckBox中控件的事件监听器
     */
    private class ClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.view_flash_on:
                    setCheckOff();
                    customCheckBoxChangeListener.customCheckBoxOff(getId());
                    break;
                case R.id.view_flash_off:
                    setCheckOn();
                    customCheckBoxChangeListener.customCheckBoxOn(getId());
                    break;
            }
        }
    }
}
