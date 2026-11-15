package com.example.myapplication;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Reads AAOS vehicle speed on Pleos. Does not drive the tutorial simulation. */
final class VehicleSpeedReceiver {
    static final String PERMISSION = "android.car.permission.CAR_SPEED";
    private static final String TAG = "CarityVehicle";
    interface Listener {
        void onSpeed(float kilometresPerHour);
        void onUnavailable(String reason);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Car car;
    private CarPropertyManager manager;
    private CarPropertyManager.CarPropertyEventCallback callback;
    private int generation;
    private boolean active;
    private Float lastLoggedMps;
    private final Runnable stale = this::onStale;

    private void onStale() {
        if (active) {
            Log.w(TAG, "SPEED_UNAVAILABLE reason=callback_timeout");
            listener.onUnavailable("수신 대기");
        }
    }

    VehicleSpeedReceiver(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (active) return;
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            listener.onUnavailable("권한 필요");
            Log.w(TAG, "SPEED_PERMISSION_MISSING");
            return;
        }
        active = true;
        final int session = ++generation;
        lastLoggedMps = null;
        listener.onUnavailable("연결 중");
        try {
            // Do not wait for car service on the UI thread.
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                    (connectedCar, ready) -> {
                        if (!active || generation != session) return;
                        if (!ready) {
                            manager = null;
                            callback = null;
                            main.removeCallbacks(stale);
                            listener.onUnavailable("연결 끊김");
                            Log.w(TAG, "SPEED_SERVICE_DISCONNECTED");
                            return;
                        }
                        Log.i(TAG, "SPEED_SERVICE_CONNECTED");
                        subscribe(connectedCar, session);
                    });
            if (car == null) {
                Log.w(TAG, "SPEED_SERVICE_UNAVAILABLE");
                listener.onUnavailable("지원 안 됨");
                active = false;
            } else {
                main.postDelayed(stale, 5000);
            }
        } catch (RuntimeException | LinkageError e) {
            fail("SPEED_INITIALIZATION_FAILED", e);
            stop();
        }
    }

    private void subscribe(Car connectedCar, int session) {
        try {
            manager = (CarPropertyManager) connectedCar.getCarManager(Car.PROPERTY_SERVICE);
            if (manager == null) throw new IllegalStateException("No car property service");
            callback = new CarPropertyManager.CarPropertyEventCallback() {
                @Override public void onChangeEvent(CarPropertyValue value) {
                    if (!active || generation != session
                            || value.getPropertyId() != VehiclePropertyIds.PERF_VEHICLE_SPEED) return;
                    main.removeCallbacks(stale);
                    Object raw = value.getValue();
                    if (value.getStatus() != CarPropertyValue.STATUS_AVAILABLE
                            || !(raw instanceof Float) || !Float.isFinite((Float) raw)) {
                        lastLoggedMps = null;
                        Log.w(TAG, "SPEED_UNAVAILABLE status=" + value.getStatus());
                        listener.onUnavailable("수신 불가");
                        return;
                    }
                    float metresPerSecond = (Float) raw;
                    float kilometresPerHour = metresPerSecond * 3.6f;
                    if (lastLoggedMps == null || Float.compare(lastLoggedMps, metresPerSecond) != 0) {
                        Log.i(TAG, "SPEED_RECEIVED speedMps=" + metresPerSecond
                                + " speedKmh=" + kilometresPerHour);
                        lastLoggedMps = metresPerSecond;
                    }
                    listener.onSpeed(kilometresPerHour);
                    main.postDelayed(stale, 5000);
                }

                @Override public void onErrorEvent(int propertyId, int areaId) {
                    if (!active || generation != session) return;
                    lastLoggedMps = null;
                    Log.e(TAG, "SPEED_PROPERTY_ERROR propertyId=" + propertyId + " areaId=" + areaId);
                    listener.onUnavailable("수신 오류");
                }
            };
            // registerCallback works on the Android version shipped by the Pleos AVD.
            if (!manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_NORMAL)) {
                throw new IllegalStateException("Vehicle speed subscription rejected");
            }
            Log.i(TAG, "SPEED_REGISTER_RETURNED awaitingCallback=true");
        } catch (RuntimeException | LinkageError e) {
            fail("SPEED_SUBSCRIPTION_FAILED", e);
            stop();
        }
    }

    private void fail(String message, Throwable error) {
        Log.e(TAG, message, error);
        listener.onUnavailable(error instanceof SecurityException ? "권한 필요" : "연결 실패");
    }

    void stop() {
        active = false;
        generation++;
        main.removeCallbacks(stale);
        if (manager != null && callback != null) {
            try {
                manager.unregisterCallback(callback);
                Log.i(TAG, "SPEED_UNREGISTER_RETURNED");
            } catch (RuntimeException | LinkageError e) {
                Log.e(TAG, "SPEED_UNSUBSCRIBE_FAILED", e);
            }
        }
        manager = null;
        callback = null;
        if (car != null) {
            try {
                car.disconnect();
                Log.i(TAG, "SPEED_SERVICE_RELEASED");
            } catch (RuntimeException | LinkageError e) {
                Log.e(TAG, "SPEED_RELEASE_FAILED", e);
            }
            car = null;
        }
    }
}
