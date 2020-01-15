package com.example.myapplication.Activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class PermissionManager {
    public static final int REQUEST_PERMISSIONS = 0;

    private Context ct;
    private ArrayList<String> needPermissionList;

    private PermissionManager() {
        needPermissionList = new ArrayList<String>();

        needPermissionList.add(Manifest.permission.CAMERA);
        needPermissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        needPermissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        needPermissionList.add(Manifest.permission.INTERNET);
        needPermissionList.add(Manifest.permission.ACCESS_NETWORK_STATE);
        needPermissionList.add(Manifest.permission.RECORD_AUDIO);
    }

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
            ArrayList<String> permissions = new ArrayList<String>();

            for (String perm : needPermissionList) {
                if (ContextCompat.checkSelfPermission(this.ct, perm) == PackageManager.PERMISSION_DENIED) {
                    permissions.add(perm);
                }
            }

            int size = permissions.size();

            if (size > 0) {
                String[] permArray = new String[size];

                permissions.toArray(permArray);
                ActivityCompat.requestPermissions((Activity) this.ct, permArray, REQUEST_PERMISSIONS);

                return true;
            } else if (size == 0) {
                return true;
            }
        }

        return false;
    }
}