package com.cubi.frameworktestapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


public class PortrateActivity extends BaseCameraActivity {

    public static void startActivity(Activity activity) {
        Intent intent = new Intent(activity, PortrateActivity.class);
        activity.startActivity(intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portrate);
        onCreateActivity();
        videoWidth = 1080;
        videoHeight = 1920;
        cameraWidth = 1920;
        cameraHeight = 1080;
    }
}
