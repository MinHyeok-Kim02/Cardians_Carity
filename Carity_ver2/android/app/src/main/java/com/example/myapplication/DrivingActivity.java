package com.example.myapplication;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

/** Read-only driving UI. No simulation, animation, settings, permission prompts or audio. */
public final class DrivingActivity extends ComponentActivity {
    private static final String TAG = "CarityVehicle";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::refreshSignals;
    private VehicleDrivingMonitor drivingMonitor;
    private VehicleSpeedReceiver speedReceiver;
    private PleosSteeringReceiver steeringReceiver;
    private TextView speedText, steeringText, stateText, permissionText;
    private Float speedKmh, steeringDegrees;
    private long speedAt, steeringAt;
    private boolean resumed, openingParked;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildScreen();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { moveTaskToBack(true); }
        });
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(16, 27, 37));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left + dp(24), bars.top + dp(20),
                    bars.right + dp(24), bars.bottom + dp(20));
            return insets;
        });
        addHeader(root);
        stateText = text("차량 상태 확인 중", 24, Color.rgb(154, 178, 192));
        root.addView(stateText);
        LinearLayout readings = new LinearLayout(this);
        readings.setGravity(Gravity.CENTER_VERTICAL);
        speedText = addReading(readings, "차량 속도", "km/h");
        steeringText = addReading(readings, "핸들 각도", "°");
        root.addView(readings, new LinearLayout.LayoutParams(-1, 0, 1));
        permissionText = text("", 24, Color.rgb(255, 211, 133));
        root.addView(permissionText);
        root.addView(text("3D 골목 · 수동 조작은 정차 후 사용", 24, Color.rgb(154, 178, 192)));
        setContentView(root);
        root.requestApplyInsets();
    }

    private void addHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Carity · 주행 정보", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(this);
        close.setText("닫기");
        close.setTextSize(24);
        close.setMinHeight(dp(64));
        close.setOnClickListener(view -> moveTaskToBack(true));
        header.addView(close, new LinearLayout.LayoutParams(dp(140), dp(72)));
        root.addView(header);
    }

    private TextView addReading(LinearLayout parent, String label, String unit) {
        LinearLayout card = column();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(24, 43, 57));
        background.setCornerRadius(dp(18));
        card.setBackground(background);
        TextView title = text(label, 26, Color.rgb(154, 178, 192));
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView value = text("--", 64, Color.WHITE);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        card.addView(value);
        TextView units = text(unit, 26, Color.rgb(104, 229, 183));
        units.setGravity(Gravity.CENTER);
        card.addView(units);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(290), 1);
        params.setMargins(dp(12), dp(24), dp(12), dp(24));
        parent.addView(card, params);
        return value;
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        openingParked = false;
        clearSignals();
        Log.i(TAG, "DRIVING_PANEL_RESUMED");
        if (Build.VERSION.SDK_INT < 29
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            openParkedScreen();
            return;
        }
        if (drivingMonitor == null)
            drivingMonitor = new VehicleDrivingMonitor(this, this::onRestrictions);
        drivingMonitor.start();
        if (!openingParked) {
            startSignalReceivers();
            main.post(refresh);
        }
    }

    private void onRestrictions(boolean known, boolean requiresDo, int flags) {
        if (!resumed || openingParked) return;
        Log.i(TAG, "DRIVING_PANEL_POLICY known=" + known + " requiresDO=" + requiresDo
                + " flags=0x" + Integer.toHexString(flags));
        if (!known) {
            stateText.setText("차량 상태 확인 중");
            return;
        }
        if (!requiresDo) {
            openParkedScreen();
            return;
        }
        // Only two short, read-only vehicle values. No video, keyboard, setup or simulation.
        stateText.setText("주행 중 · 차량 신호 표시");
    }

    private void startSignalReceivers() {
        if (checkSelfPermission(VehicleSpeedReceiver.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            permissionText.setText("");
            if (speedReceiver == null) {
                speedReceiver = new VehicleSpeedReceiver(this, new VehicleSpeedReceiver.Listener() {
                    @Override public void onSpeed(float value) {
                        if (!resumed) return;
                        speedKmh = value;
                        speedAt = SystemClock.elapsedRealtime();
                    }
                    @Override public void onUnavailable(String reason) {
                        speedKmh = null;
                        if (resumed) permissionText.setText("속도 신호 대기 · 정차 후 연결 확인");
                    }
                });
            }
            speedReceiver.start();
        } else {
            // Permission requests and setup belong to the parked screen.
            permissionText.setText("속도 권한은 정차 후 허용해 주세요");
        }
        if (steeringReceiver == null) {
            steeringReceiver = new PleosSteeringReceiver(this, value -> {
                if (!resumed) return;
                steeringDegrees = value;
                steeringAt = SystemClock.elapsedRealtime();
            });
        }
        steeringReceiver.start();
    }

    private void refreshSignals() {
        if (!resumed || openingParked) return;
        long now = SystemClock.elapsedRealtime();
        boolean freshSpeed = speedKmh != null && now - speedAt <= 5000;
        boolean freshSteering = steeringDegrees != null && now - steeringAt <= 5000;
        speedText.setText(freshSpeed ? String.format(Locale.KOREA, "%.1f", speedKmh) : "--");
        steeringText.setText(freshSteering
                ? String.format(Locale.KOREA, "%+.1f", steeringDegrees) : "--");
        if (freshSpeed) permissionText.setText("");
        main.postDelayed(refresh, 500);
    }

    private void openParkedScreen() {
        if (!resumed || openingParked) return;
        openingParked = true;
        main.removeCallbacks(refresh);
        Log.i(TAG, "DRIVING_PANEL_TO_PARKED");
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(refresh);
        if (drivingMonitor != null) drivingMonitor.stop();
        if (speedReceiver != null) speedReceiver.stop();
        if (steeringReceiver != null) steeringReceiver.stop();
        clearSignals();
        Log.i(TAG, "DRIVING_PANEL_PAUSED");
        super.onPause();
    }

    private void clearSignals() {
        speedKmh = null;
        steeringDegrees = null;
        speedText.setText("--");
        steeringText.setText("--");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setPadding(dp(8), dp(8), dp(8), dp(8));
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
