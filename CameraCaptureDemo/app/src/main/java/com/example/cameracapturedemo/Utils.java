package com.example.cameracapturedemo;

import android.content.Context;

public class Utils {

    public static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}
