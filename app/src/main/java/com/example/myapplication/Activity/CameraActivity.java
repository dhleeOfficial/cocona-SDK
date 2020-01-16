package com.example.myapplication.Activity;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import framework.Engine.CameraEngine;
import framework.Enum.Exposure;
import framework.Enum.Filter;
import framework.Enum.LensFacing;
import framework.Enum.Mode;
import framework.Enum.RecordSpeed;
import framework.Enum.TouchType;

import com.example.myapplication.R;

public class CameraActivity extends AppCompatActivity {
    //private TextureView tv;
    private SurfaceView sv;


    private RelativeLayout rl;

    private ToggleButton flash;
    private ToggleButton lens;
    private ToggleButton record;
    private ToggleButton live;

    private Button zoom;
    private Button bright;
    private Button dark;
    private Button filter;
    private Button mode;

    private RadioGroup radioGroup;
    private RadioGroup radioGroup2;

    private CameraEngine engine;
    private CameraEngine.Util engineUtil;
    private CameraEngine.Util.SingleTouchEventHandler touchEventHandler;

    private final Activity myActivity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);

        rl = findViewById(R.id.relativeLayout);

        engine = new CameraEngine(myActivity, rl);
        engine.startEngine();

        engineUtil = new CameraEngine.Util();
        touchEventHandler = new CameraEngine.Util.SingleTouchEventHandler();

        //tv = findViewById(R.id.textureView);
        sv = findViewById(R.id.surfaceView);

        flash = findViewById(R.id.flashBtn);
        lens = findViewById(R.id.lensBtn);
        record = findViewById(R.id.recordBtn);
        zoom = findViewById(R.id.zoomBtn);
        bright = findViewById(R.id.brightBtn);
        dark = findViewById(R.id.darkBtn);
        radioGroup = findViewById(R.id.radioGroup);
        radioGroup2 = findViewById(R.id.radioGroup2);

        live = findViewById(R.id.liveBtn);
        filter = findViewById(R.id.filterBtn);
        mode = findViewById(R.id.modeBtn);

        CompoundButton.OnCheckedChangeListener checkListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (buttonView == flash) {
                        engine.flash(isChecked);
                    } else if (buttonView == lens) {
                        if (isChecked == true) {
                            engine.lensFacing(LensFacing.FRONT);
                        } else {
                            engine.lensFacing(LensFacing.BACK);
                        }

                    } else if (buttonView == record) {
                        if (isChecked == true) {
                            if (engine.getMode() == Mode.TRAVEL || engine.getMode() == Mode.DAILY) {
                                radioGroup.setVisibility(View.VISIBLE);
                                radioGroup2.setVisibility(View.VISIBLE);
                            }
                        } else {
                            radioGroup.check(R.id.normalBtn);
                            radioGroup.setVisibility(View.INVISIBLE);

                            radioGroup2.check(R.id.resumeBtn);
                            radioGroup2.setVisibility(View.INVISIBLE);
                        }

                        engine.record(isChecked);
                    } else if (buttonView == live) {
                        engine.live(isChecked);
                    }
            }
        };

        RadioGroup.OnCheckedChangeListener changeListener = new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.slowBtn) {
                    engine.recordSpeed(RecordSpeed.SLOW);
                } else if (checkedId == R.id.normalBtn) {
                    engine.recordSpeed(RecordSpeed.NORMAL);
                } else if (checkedId == R.id.fastBtn) {
                    engine.recordSpeed(RecordSpeed.FAST);
                }
            }
        };

        RadioGroup.OnCheckedChangeListener changeListener2 = new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.pauseBtn) {
                    engine.recordSpeed(RecordSpeed.PAUSE);
                } else if (checkedId == R.id.resumeBtn) {
                    engine.recordSpeed(RecordSpeed.RESUME);
                }
            }
        };

        flash.setOnCheckedChangeListener(checkListener);
        lens.setOnCheckedChangeListener(checkListener);
        record.setOnCheckedChangeListener(checkListener);
        live.setOnCheckedChangeListener(checkListener);
        radioGroup.setOnCheckedChangeListener(changeListener);
        radioGroup2.setOnCheckedChangeListener(changeListener2);

        Button.OnClickListener clickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == zoom) {
                    Toast.makeText(CameraActivity.this, "Please Touch Screen", Toast.LENGTH_LONG);
                } else if (v == bright) {
                    engine.exposure(Exposure.BRIGHT);
                } else if (v == dark) {
                    engine.exposure(Exposure.DARK);
                } else if (v == filter) {
                    PopupMenu popupMenu = new PopupMenu(CameraActivity.this, filter);
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch(item.getItemId()) {
                                case R.id.filter_off : {
                                    engine.filter(Filter.OFF);

                                    return true;
                                }
                                case R.id.filter_mono : {
                                    engine.filter(Filter.MONO);

                                    return true;
                                }
                                case R.id.filter_negative : {
                                    engine.filter(Filter.NEGATIVE);

                                    return true;
                                }
                                case R.id.filter_solarize : {
                                    engine.filter(Filter.SOLARIZE);

                                    return true;
                                }
                                case R.id.filter_sepia : {
                                    engine.filter(Filter.SEPIA);

                                    return true;
                                }
                                case R.id.filter_posterize : {
                                    engine.filter(Filter.POSTERIZE);

                                    return true;
                                }
                                case R.id.filter_whiteboard : {
                                    engine.filter(Filter.WHITEBOARD);

                                    return true;
                                }
                                case R.id.filter_blackboard : {
                                    engine.filter(Filter.BLACKBOARD);

                                    return true;
                                }
                                case R.id.filter_aqua : {
                                    engine.filter(Filter.AQUA);

                                    return true;
                                }
                            }
                            return false;
                        }
                    });
                    MenuInflater menuInflater = popupMenu.getMenuInflater();

                    menuInflater.inflate(R.menu.filter_menu, popupMenu.getMenu());
                    popupMenu.show();
                } else if (v == mode) {
                    PopupMenu popupMenu = new PopupMenu(CameraActivity.this, mode);
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.travel : {
                                    record.setVisibility(View.VISIBLE);
                                    live.setVisibility(View.INVISIBLE);

                                    engine.mode(Mode.TRAVEL);

                                    return true;
                                }
                                case R.id.event : {
                                    record.setVisibility(View.VISIBLE);
                                    live.setVisibility(View.INVISIBLE);

                                    engine.mode(Mode.EVENT);

                                    return true;
                                }
                                case R.id.live : {
                                    record.setVisibility(View.INVISIBLE);
                                    live.setVisibility(View.VISIBLE);

                                    engine.mode(Mode.LIVE);

                                    return true;
                                }
                                case R.id.daily : {
                                    record.setVisibility(View.VISIBLE);
                                    live.setVisibility(View.INVISIBLE);

                                    engine.mode(Mode.DAILY);

                                    return true;
                                }
                            }
                            return false;
                        }
                    });
                    MenuInflater menuInflater = popupMenu.getMenuInflater();

                    menuInflater.inflate(R.menu.mode_menu, popupMenu.getMenu());
                    popupMenu.show();
                }
            }
        };

        zoom.setOnClickListener(clickListener);
        bright.setOnClickListener(clickListener);
        dark.setOnClickListener(clickListener);
        filter.setOnClickListener(clickListener);
        mode.setOnClickListener(clickListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //engine.startPreview(tv);
        engine.startPreview(sv);
    }

    @Override
    protected void onPause() {
        super.onPause();

        engine.stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        engine.stopEngine();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() == 2) {
            engine.zoom(engineUtil.getFingerSpacing(event));

            return true;
        } else if (event.getPointerCount() == 1) {
            TouchType touchType = touchEventHandler.getTouchTypeFromTouchEvent(event);

            if (touchType == TouchType.AREAFOCUS) {
                engine.areaFocus(new PointF(event.getX(), event.getY()));
            } else if (touchType == TouchType.LOCKFOCUS) {
                engine.lockFocus(new PointF(event.getX(), event.getY()));
            } else if (touchType == TouchType.EXPOSURECHANGE) {
                //TODO
            }

            return true;
        }
        return super.onTouchEvent(event);
    }
}
