package com.example.myapplication.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import com.example.myapplication.R;

public class MainActivity extends AppCompatActivity {
    private PermissionManager pm;
    private final Activity myActivity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.startBtn).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pm = PermissionManager.getInstance();

                        if (pm != null) {
                            if (pm.getContext() != myActivity) {
                                pm.setContext(myActivity);
                            }
                            if (pm.checkStart() == true) {
                                changeCameraActivity();
                            }
                        }
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case PermissionManager.REQUEST_PERMISSIONS : {
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    this.changeCameraActivity();
                } else {
                    // RE-INVOKE PERMISSION
                }
            }
            break;
        }
    }

    public void changeCameraActivity() {
        Intent intent = new Intent(myActivity, CameraActivity.class);

        startActivity(intent);
    }
}
