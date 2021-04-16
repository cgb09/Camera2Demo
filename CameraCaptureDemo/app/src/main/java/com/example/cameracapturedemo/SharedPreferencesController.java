package com.example.cameracapturedemo;

import android.content.Context;
import android.content.SharedPreferences;

import static android.content.Context.MODE_PRIVATE;

public class SharedPreferencesController {
    private static final String SP_FILE_NAME = "yanwei";
    private static SharedPreferences mSp;

    private SharedPreferencesController(Context context) {
        if (mSp == null) {
            mSp = context.getSharedPreferences(SP_FILE_NAME, MODE_PRIVATE);//yanwei.xml
        }
    }

    private static SharedPreferencesController instance = null;

    public synchronized static SharedPreferencesController getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesController(context.getApplicationContext());
        }
        return instance;
    }

    public static void spPutString(String key, String value) {
        SharedPreferences.Editor editor = mSp.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public static String spGetString(String key) {
        String value = mSp.getString(key, "");
        return value;
    }

    public static void spPutBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = mSp.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    public static boolean spGetBoolean(String key) {
        boolean value = mSp.getBoolean(key, false);
        return value;
    }
}
