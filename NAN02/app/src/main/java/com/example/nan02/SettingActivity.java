package com.example.nan02;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;

public class SettingActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    private ImageView mBack;
    private Switch mSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFullScreenActivity();
        setContentView(R.layout.activity_setting);
        intView();
    }
    private void intView() {
        mBack = findViewById(R.id.back);
        mSwitch = findViewById(R.id.water_mark_switch);
        mSwitch.setOnCheckedChangeListener(this);
        mBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
//                Intent intent = new Intent(SettingActivity.this, MainActivity.class);
//                startActivity(intent);
            }
        });
        boolean addMark = SharedPreferencesController.getInstance(this).spGetBoolean("add_water_mark");
        mSwitch.setChecked(addMark);
    }

    private void requestFullScreenActivity() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        SharedPreferencesController.getInstance(this).spPutBoolean("add_water_mark",isChecked);
    }


}
