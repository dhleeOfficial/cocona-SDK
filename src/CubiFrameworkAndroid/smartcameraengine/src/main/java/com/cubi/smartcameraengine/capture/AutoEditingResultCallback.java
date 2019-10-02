package com.cubi.smartcameraengine.capture;

import android.content.res.Configuration;
import android.content.res.Resources;

import com.cubi.smartcameraengine.LensFacing;

import static com.cubi.smartcameraengine.CameraRecorder.videoPath;
import static com.cubi.smartcameraengine.CameraRecorder.txtPath;

public class AutoEditingResultCallback {
    AutoEditingResultCallbackInterface autoeditingResult;

    public AutoEditingResultCallback(AutoEditingResultCallbackInterface callback) {
        this.autoeditingResult = callback;
    }

    private LensFacing lensFacing;
    private Resources resources;

    // cameraPostion :
    //          .back : 1
    //          .front : 2
    // cameraOrientation :
    //          .portrait : 1
    //          .portraitUpsideDown : 2
    //          .landscapeRight : 3
    //          .landscapeLeft : 4

    private int cameraPosition; {
        if(true){
            cameraPosition = 2;
        } else{
            cameraPosition = 1;
        }
    }

    private int cameraOrientation; {
//        if(resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
        if (true) {
            cameraOrientation = 3;
        } else{
            cameraOrientation = 1;
        }
    }



    public void callMe() {
        System.out.println("AutoEditingResultCallback Call");
        if(autoeditingResult !=null){
            String data = "videoFile = " + videoPath + "\nscoreFile= " + txtPath + "\ncameraOrientation = " + cameraOrientation + "\ncameraPosition = " + cameraPosition;
            System.out.println(data);
            autoeditingResult.onCall(data);
        }
    }
}
