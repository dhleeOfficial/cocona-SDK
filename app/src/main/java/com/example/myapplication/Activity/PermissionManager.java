package com.example.myapplication.Activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {
    public static final int REQUEST_CAMERA = 0;
    private Context ct;

    private PermissionManager() {}

    private static class InstanceHolder {
        private static final PermissionManager inst = new PermissionManager();
    }

    public static PermissionManager getInstance() {
        return InstanceHolder.inst;
    }

    public void setContext(Context context) {
        if (this.ct != context) {
            this.ct = context;
        }
    }

    public final Context getContext() {
        return this.ct;
    }

    public boolean checkStart() {
        if (this.ct != null) {
            // ContextCompat.checkSelfPermission
            // Self check function
            // return value : PackageManager.PERMISSION_DENIED or GRANTED
            if (ContextCompat.checkSelfPermission(this.ct, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                // ActivityCompat.shouldShowRequestPermissionRationale
                // Define why need to that Permission
                // true = Refuse by user
                if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) this.ct, Manifest.permission.CAMERA) == false) {
                    // ActivityCompat.requestPermissions
                    // Request permission and Process callback
                    ActivityCompat.requestPermissions((Activity) this.ct, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
                }
            } else {
                return true;
            }
        }
        return false;
    }
}
