package com.example.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;

/** Coordinates user actions, the simulation, rendering and warning output. */
public final class MainActivity extends Activity {
    private final AlleySimulation model = new AlleySimulation();
    private AlleyView alley;
    private final VehicleMotionController motion = new VehicleMotionController(model);
    private final Handler animation = new Handler(Looper.getMainLooper());
    private final Runnable motionFrame = this::updateMotionFrame;
    private VehicleDrivingMonitor drivingMonitor;
    private Switch alleyToggle;
    private LinearLayout simulationPanel, homePanel, manualControls;
    private TextView steeringStatus, restrictionStatus, motionStatus;
    private boolean alleyEnabled, uxKnown, requiresDo = true;
    private long lastMotionUi, lastMotionLog;
    private VehicleMotionController.Status shownMotionStatus;
    private TextView gaps, event, audioStatus;
    private Switch tutorialSwitch;
    private WarningAudio audio;
    private int eventCount;
    private PleosSteeringReceiver pleosSteering;
    private VehicleSpeedReceiver vehicleSpeed;
    private TextView speedStatus;
    private Button speedPermission;
    private boolean resumed;
    private boolean openingDrivingPanel;
    private static final int SPEED_PERMISSION_REQUEST = 41;
    private static final String SPEED_PERMISSION = "android.car.permission.CAR_SPEED";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        restoreSimulation(state);
        buildScreen(state);
        initializeAudio();
        setAlleyVisible(alleyEnabled);
        refreshScreen();
    }

    private void restoreSimulation(Bundle state) {
        alleyEnabled = state != null ? state.getBoolean("alleyEnabled", false)
                : getPreferences(MODE_PRIVATE).getBoolean("alleyEnabled", false);
        if (state == null) return;
        model.x = state.getDouble("x", model.x);
        model.y = state.getDouble("y", model.y);
        model.heading = state.getDouble("heading", model.heading);
        model.pollEntries(); // Restoring a near state must not replay its warning.
        eventCount = state.getInt("count");
    }

    private void buildScreen(Bundle state) {
        LinearLayout root = createRoot();
        setContentView(root);
        root.requestApplyInsets();
        addHeader(root);
        addHome(root);
        addSimulationAndControls(root, state);
        alleyToggle.setOnCheckedChangeListener((button, checked) -> setAlleyVisible(checked));
    }

    private LinearLayout createRoot() {
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(16, 27, 37));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        return root;
    }

    private void addHeader(LinearLayout root) {
        LinearLayout titleRow = new LinearLayout(this);
        TextView title = text("On-sense", 28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        alleyToggle = new Switch(this);
        alleyToggle.setText("골목 보기 켜기/끄기");
        alleyToggle.setTextSize(24);
        alleyToggle.setTextColor(Color.WHITE);
        alleyToggle.setMinHeight(dp(72));
        alleyToggle.setPadding(dp(20), 0, dp(24), 0);
        alleyToggle.setChecked(alleyEnabled);
        titleRow.addView(alleyToggle);
        root.addView(titleRow);
        LinearLayout signals = new LinearLayout(this);
        speedStatus = text("차량 속도  -- km/h", 22);
        steeringStatus = text("핸들 각도  --°", 22);
        signals.addView(speedStatus, new LinearLayout.LayoutParams(0, -2, 1));
        signals.addView(steeringStatus, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(signals);
        speedPermission = new Button(this);
        speedPermission.setText("속도 읽기 허용");
        speedPermission.setMinHeight(dp(64));
        speedPermission.setOnClickListener(view -> requestSpeedPermission());
        root.addView(speedPermission);
        restrictionStatus = text("주행 제한 상태 확인 중", 18);
        restrictionStatus.setTextColor(Color.rgb(149, 173, 188));
        root.addView(restrictionStatus);
        TextView disclaimer = text("좌우 간격은 가상 벽 기준입니다 · 실차 주변 장애물 실측값이 아닙니다", 18);
        disclaimer.setTextColor(Color.rgb(255, 211, 133));
        root.addView(disclaimer);
        gaps = text("", 26);
        gaps.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(gaps);
    }

    private void addHome(LinearLayout root) {
        homePanel = column();
        homePanel.setPadding(dp(32), dp(40), dp(32), dp(24));
        homePanel.setBackgroundColor(Color.rgb(22, 37, 49));
        TextView heading = text("차량 신호 모니터", 32);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        homePanel.addView(heading);
        homePanel.addView(text("골목 보기를 켜면 3D 가상 골목을 표시합니다.", 24));
        homePanel.addView(text("수신한 속도와 핸들 각도로 가상 차량을 움직입니다.\n"
                + "가상 환경의 간격은 실제 차량 주변 거리와 다릅니다.", 22));
        homePanel.addView(text("플랫폼이 주행 중 사용을 제한하면 화면 조작과 가상 주행이 중지됩니다.", 20));
        root.addView(homePanel, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void setAlleyVisible(boolean enabled) {
        alleyEnabled = enabled;
        motion.setEnabled(enabled);
        if (alleyToggle.isChecked() != enabled) alleyToggle.setChecked(enabled);
        homePanel.setVisibility(enabled ? View.GONE : View.VISIBLE);
        simulationPanel.setVisibility(enabled ? View.VISIBLE : View.GONE);
        gaps.setVisibility(enabled ? View.VISIBLE : View.GONE);
        getPreferences(MODE_PRIVATE).edit().putBoolean("alleyEnabled", enabled).apply();
        animation.removeCallbacks(motionFrame);
        if (resumed && enabled) {
            alley.onResume();
            animation.post(motionFrame);
        } else {
            alley.onPause();
            if (audio != null) audio.cancel();
        }
        Log.i("OnSensePleos", "ALLEY_VIEW enabled=" + enabled);
    }

    private void addSimulationAndControls(LinearLayout root, Bundle state) {
        LinearLayout body = new LinearLayout(this);
        simulationPanel = body;
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        alley = new AlleyView(this, model);
        body.addView(alley, new LinearLayout.LayoutParams(0, -1, 1));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(createControls(state));
        body.addView(scroll, new LinearLayout.LayoutParams(dp(384), -1));
    }

    private LinearLayout createControls(Bundle state) {
        LinearLayout controls = column();
        controls.setPadding(dp(20), dp(24), dp(20), dp(20));
        controls.setBackgroundColor(Color.rgb(22, 37, 49));
        TextView section = text("DRIVE CONTROL", 16);
        section.setTextColor(Color.rgb(104, 229, 183));
        controls.addView(section);
        motionStatus = text("차량 신호 수신 대기", 22);
        controls.addView(motionStatus);
        controls.addView(text("가상 조향비 16:1 · 축거 2.7m\n실차 보정값이 아닌 시연용 모델", 16));
        controls.addView(text("차체 옆 라인으로\n좌우 여유를 확인하세요", 24));
        controls.addView(text("초록  여유  >60cm\n주황  주의  30–60cm\n빨강  근접  ≤30cm", 20));
        event = text("", 20);
        event.setPadding(dp(12), dp(20), dp(12), dp(20));
        controls.addView(event);
        controls.addView(createTutorialSwitch(state));
        audioStatus = text("Android TTS · 초기화 중", 18);
        controls.addView(audioStatus);
        controls.addView(text("수동 테스트 · 정지/신호 대기 시", 18));
        manualControls = column();
        controls.addView(manualControls);
        addDrivingButtons(manualControls);
        TextView note = text("앱 내부 시뮬레이션\n표시 간격은 가상 값입니다", 16);
        note.setTextColor(Color.rgb(149, 173, 188));
        note.setPadding(dp(12), dp(20), dp(12), dp(4));
        controls.addView(note);
        return controls;
    }

    private Switch createTutorialSwitch(Bundle state) {
        tutorialSwitch = new Switch(this);
        tutorialSwitch.setText("튜토리얼 음성");
        tutorialSwitch.setTextSize(22);
        tutorialSwitch.setTextColor(Color.WHITE);
        tutorialSwitch.setMinHeight(dp(64));
        tutorialSwitch.setPadding(dp(12), 0, dp(12), 0);
        tutorialSwitch.setChecked(state == null || state.getBoolean("tutorial", true));
        return tutorialSwitch;
    }

    private void addDrivingButtons(LinearLayout controls) {
        LinearLayout steering = new LinearLayout(this);
        controls.addView(steering);
        button(steering, "좌향 −5°", () -> steer(-model.scenario.steeringDegrees));
        button(steering, "우향 +5°", () -> steer(model.scenario.steeringDegrees));
        LinearLayout travel = new LinearLayout(this);
        controls.addView(travel);
        button(travel, "전진 25cm", this::advance);
        button(travel, "초기화", this::resetSimulation);
    }

    private void initializeAudio() {
        audio = new WarningAudio(this, status -> audioStatus.setText(status));
        audio.setTutorial(tutorialSwitch.isChecked());
        tutorialSwitch.setOnCheckedChangeListener((button, checked) -> audio.setTutorial(checked));
    }

    private void steer(double amount) {
        if (!canUseManualControls()) return;
        model.steer(amount);
        onSimulationChanged();
    }

    private void advance() {
        if (!canUseManualControls()) return;
        model.forward();
        onSimulationChanged();
    }

    private void resetSimulation() {
        if (!canUseManualControls()) return;
        audio.cancel();
        model.reset();
        eventCount = 0;
        alley.resetBlink();
        refreshScreen();
    }

    private void onSimulationChanged() {
        dispatchProximityEntries();
        refreshScreen();
    }

    private void dispatchProximityEntries() {
        for (AlleySimulation.Side side : model.pollEntries()) {
            eventCount++;
            alley.entered(side);
            audio.enqueue(side);
            Log.i("OnSense", String.format(Locale.US,
                    "NEAR_ENTER side=%s gapCm=%.2f event=%d", side, model.gap(side) * 100, eventCount));
        }
    }

    private void refreshScreen() {
        updateDistanceText();
        updateEventText();
        alley.publish();
        logSimulationState();
    }

    private void updateDistanceText() {
        gaps.setText(sideText(AlleySimulation.Side.LEFT) + "      /      " + sideText(AlleySimulation.Side.RIGHT));
        gaps.setTextColor(AlleyView.color(AlleySimulation.Level.from(Math.min(
                model.gap(AlleySimulation.Side.LEFT), model.gap(AlleySimulation.Side.RIGHT)))));
    }

    private void updateEventText() {
        event.setText(String.format(Locale.KOREA, "차량 방향  %+.0f°\n이동 거리  %.2fm\n근접 진입  %d회",
                model.heading, model.y, eventCount));
    }

    private String sideText(AlleySimulation.Side side) {
        double gap = model.gap(side);
        AlleySimulation.Level level = AlleySimulation.Level.from(gap);
        String name = level == AlleySimulation.Level.NEAR ? "근접" :
                level == AlleySimulation.Level.CAUTION ? "주의" : "여유";
        return String.format(Locale.KOREA, "%s %.0f cm · %s",
                side == AlleySimulation.Side.LEFT ? "왼쪽" : "오른쪽", gap * 100, name);
    }

    private void logSimulationState() {
        Log.d("OnSense", String.format(Locale.US,
                "STATE left=%.2fcm right=%.2fcm heading=%.0f events=%d",
                model.gap(AlleySimulation.Side.LEFT) * 100,
                model.gap(AlleySimulation.Side.RIGHT) * 100, model.heading, eventCount));
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sizeSp) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sizeSp);
        label.setTextColor(Color.WHITE);
        label.setPadding(dp(12), dp(4), dp(12), dp(4));
        return label;
    }

    private void button(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(22);
        button.setAllCaps(false);
        button.setMinHeight(dp(72));
        button.setTextColor(Color.rgb(231, 242, 247));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(39, 63, 79));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(69, 95, 112));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(76), 1);
        params.setMargins(dp(4), dp(6), dp(4), dp(6));
        row.addView(button, params);
        button.setOnClickListener(view -> action.run());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putDouble("x", model.x);
        out.putDouble("y", model.y);
        out.putDouble("heading", model.heading);
        out.putInt("count", eventCount);
        out.putBoolean("tutorial", tutorialSwitch.isChecked());
        out.putBoolean("alleyEnabled", alleyEnabled);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        openingDrivingPanel = false;
        motion.setForeground(true);
        Log.i("OnSensePleos", "APP_RESUMED alleyEnabled=" + alleyEnabled);
        if (supportsVehicleSpeed()) {
            if (drivingMonitor == null)
                drivingMonitor = new VehicleDrivingMonitor(this, this::onRestrictions);
            drivingMonitor.start();
        } else {
            onRestrictions(true, false, 0);
        }
        if (openingDrivingPanel) return;
        startVehicleSpeed();
        if (pleosSteering == null) {
            pleosSteering = new PleosSteeringReceiver(this, angle -> {
                if (!resumed) return;
                if (angle == null) {
                    motion.clearSteering();
                    steeringStatus.setText("핸들 각도  --° · 수신 불가");
                } else {
                    motion.steering(angle, SystemClock.elapsedRealtime());
                    steeringStatus.setText(String.format(Locale.KOREA, "핸들 각도  %+.1f°", angle));
                }
            });
        }
        pleosSteering.start();
        if (alleyEnabled) {
            alley.onResume();
            animation.post(motionFrame);
        }
        if (audio != null) audio.resume();
    }

    @Override protected void onPause() {
        resumed = false;
        animation.removeCallbacks(motionFrame);
        motion.setForeground(false);
        uxKnown = false;
        requiresDo = true;
        motion.setRestrictions(false, true);
        if (drivingMonitor != null) drivingMonitor.stop();
        if (vehicleSpeed != null) vehicleSpeed.stop();
        speedStatus.setText("차량 속도  -- km/h · 수신 대기");
        steeringStatus.setText("핸들 각도  --° · 수신 대기");
        if (pleosSteering != null) pleosSteering.stop();
        if (audio != null) audio.pause();
        if (alley != null) alley.onPause();
        Log.i("OnSensePleos", "APP_PAUSED alleyEnabled=" + alleyEnabled);
        super.onPause();
    }

    private void onRestrictions(boolean known, boolean requiresDo, int flags) {
        if (!resumed) return;
        uxKnown = known;
        this.requiresDo = requiresDo;
        motion.setRestrictions(known, requiresDo);
        alleyToggle.setEnabled(known && !requiresDo);
        restrictionStatus.setText(!known ? "주행 제한 상태 확인 중 · 가상 주행 일시정지"
                : requiresDo ? "주행 중 사용 제한 · 가상 주행 일시정지"
                : "플랫폼 주행 제한 없음");
        if ((!known || requiresDo) && audio != null) audio.cancel();
        updateManualControls();
        if (known && requiresDo && !openingDrivingPanel) {
            openingDrivingPanel = true;
            animation.removeCallbacks(motionFrame);
            motion.setForeground(false);
            if (audio != null) audio.cancel();
            Log.i("OnSensePleos", "PARKED_TO_DRIVING_PANEL");
            startActivity(new Intent(this, DrivingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }

    private void updateMotionFrame() {
        if (!resumed || !alleyEnabled || openingDrivingPanel) return;
        long now = SystemClock.elapsedRealtime();
        boolean moved = motion.tick(now);
        if (moved) {
            dispatchProximityEntries();
            alley.publish();
        }
        if (now - lastMotionUi >= 200 || shownMotionStatus != motion.status()) {
            lastMotionUi = now;
            updateDistanceText();
            updateEventText();
            shownMotionStatus = motion.status();
            motionStatus.setText(motionStatusText(shownMotionStatus));
            updateManualControls();
        }
        if (now - lastMotionLog >= 1000) {
            lastMotionLog = now;
            Log.i("OnSensePleos", String.format(Locale.US,
                    "VIRTUAL_MOTION status=%s x=%.3f y=%.3f headingDeg=%.3f",
                    motion.status(), model.x, model.y, model.heading));
        }
        animation.postDelayed(motionFrame, 33);
    }

    private boolean canUseManualControls() {
        return resumed && uxKnown && !requiresDo
                && motion.status() != VehicleMotionController.Status.MOVING;
    }

    private void updateManualControls() {
        if (manualControls == null) return;
        boolean enabled = canUseManualControls();
        for (int i = 0; i < manualControls.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) manualControls.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) row.getChildAt(j).setEnabled(enabled);
        }
    }

    private String motionStatusText(VehicleMotionController.Status status) {
        switch (status) {
            case MOVING: return "차량 신호 연동 중";
            case STOPPED: return "정지 · 차량 속도 0";
            case WALL: return "가상 벽 도달 · 위치 초기화 필요";
            case STALE: return "신호 지연 · 가상 주행 일시정지";
            case RESTRICTED: return "주행 제한 · 가상 주행 일시정지";
            default: return "차량 신호 수신 대기";
        }
    }

    private boolean supportsVehicleSpeed() {
        return Build.VERSION.SDK_INT >= 29
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void startVehicleSpeed() {
        if (!resumed) return;
        if (!supportsVehicleSpeed()) {
            speedPermission.setVisibility(View.GONE);
            speedStatus.setText("차량 속도  -- km/h · 지원 안 됨");
            return;
        }
        if (checkSelfPermission(SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            speedPermission.setVisibility(View.VISIBLE);
            speedStatus.setText("차량 속도  -- km/h · 권한 필요");
            return;
        }
        speedPermission.setVisibility(View.GONE);
        if (vehicleSpeed == null) {
            vehicleSpeed = new VehicleSpeedReceiver(this, new VehicleSpeedReceiver.Listener() {
                @Override public void onSpeed(float kilometresPerHour) {
                    if (!resumed) return;
                    motion.speed(kilometresPerHour, SystemClock.elapsedRealtime());
                    speedStatus.setText(String.format(Locale.KOREA,
                            "차량 속도  %.1f km/h", kilometresPerHour));
                }
                @Override public void onUnavailable(String reason) {
                    motion.clearSpeed();
                    if (resumed) speedStatus.setText("차량 속도  -- km/h · " + reason);
                }
            });
        }
        vehicleSpeed.start();
    }

    private void requestSpeedPermission() {
        if (!supportsVehicleSpeed()) return;
        if (checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            startVehicleSpeed();
            return;
        }
        boolean requested = getPreferences(MODE_PRIVATE).getBoolean("speedPermissionRequested", false);
        if (shouldShowRequestPermissionRationale(SPEED_PERMISSION)) {
            new AlertDialog.Builder(this).setTitle("차량 속도 읽기")
                    .setMessage("Pleos 차량 속도를 앱에 표시하려면 속도 읽기 권한이 필요합니다.")
                    .setPositiveButton("계속", (dialog, which) -> launchSpeedPermission())
                    .setNegativeButton("취소", null).show();
        } else if (requested) {
            new AlertDialog.Builder(this).setTitle("속도 읽기 권한 필요")
                    .setMessage("앱 설정에서 차량 속도 읽기 권한을 허용할 수 있습니다.")
                    .setPositiveButton("앱 설정", (dialog, which) -> startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("취소", null).show();
        } else {
            launchSpeedPermission();
        }
    }

    private void launchSpeedPermission() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("speedPermissionRequested", true).apply();
        requestPermissions(new String[]{SPEED_PERMISSION}, SPEED_PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SPEED_PERMISSION_REQUEST) {
            if (checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                Log.i("OnSensePleos", "SPEED_PERMISSION_GRANTED");
                startVehicleSpeed();
            } else {
                Log.w("OnSensePleos", "SPEED_PERMISSION_DENIED");
                speedStatus.setText("차량 속도  -- km/h · 권한 거부");
                if (!shouldShowRequestPermissionRationale(SPEED_PERMISSION)) {
                    speedPermission.setText("속도 권한 설정");
                }
            }
        }
    }

    @Override protected void onDestroy() {
        if (audio != null) audio.release();
        super.onDestroy();
    }
}
