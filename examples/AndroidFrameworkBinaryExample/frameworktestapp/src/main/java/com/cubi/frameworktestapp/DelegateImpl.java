package com.cubi.frameworktestapp;

import com.cubi.smartcameraengine.capture.DelegateInterface;

public class DelegateImpl implements DelegateInterface {
    @Override
    public void onCall(String video, String score) {
        System.out.println("videoPath:" + video + "txtPath:" + score );
    }
}
