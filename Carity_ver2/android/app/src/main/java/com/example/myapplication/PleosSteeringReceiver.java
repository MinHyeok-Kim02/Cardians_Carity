package com.example.myapplication;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.SteeringWheel;
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener;

/** Delivers Pleos steering to the foreground UI and logs actual callbacks. */
final class PleosSteeringReceiver {
    private static final String TAG = "OnSensePleos";
    interface Observer { void onSteering(Float angle); }
    private final Context context;
    private final Observer observer;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Vehicle vehicle;
    private SteeringWheel steeringWheel;
    private volatile SteeringWheelAngleListener listener;
    private volatile boolean active;

    PleosSteeringReceiver(Context context, Observer observer) {
        this.context = context.getApplicationContext();
        this.observer = observer;
    }

    void start() {
        if (active) return;
        active = true;
        listener = new SteeringWheelAngleListener() {
            @Override public void onSteeringWheelAngleUpdated(Float angle) {
                if (!active || listener != this) return;
                if (angle == null || !Float.isFinite(angle)) {
                    Log.w(TAG, "STEERING_UNAVAILABLE angleDeg=" + angle);
                    main.post(() -> { if (active && listener == this) observer.onSteering(null); });
                    return;
                }
                // A real callback proves data delivery; initialize() alone does not.
                Log.i(TAG, "STEERING_RECEIVED angleDeg=" + angle);
                main.post(() -> { if (active && listener == this) observer.onSteering(angle); });
            }

            @Override public void onFailed(Exception e) {
                if (active && listener == this) {
                    Log.e(TAG, "STEERING_SUBSCRIPTION_FAILED", e);
                    main.post(() -> { if (active && listener == this) observer.onSteering(null); });
                }
            }
        };
        try {
            vehicle = new Vehicle(context);
            vehicle.initialize();
            Log.i(TAG, "SDK_INITIALIZE_RETURNED connectionUnverified=true");
        } catch (RuntimeException | LinkageError e) {
            Log.e(TAG, "SDK_INITIALIZATION_FAILED", e);
            observer.onSteering(null);
            stop();
            return;
        }
        try {
            steeringWheel = vehicle.getSteeringWheel();
            steeringWheel.registerSteeringWheelAngle(listener);
            Log.i(TAG, "STEERING_REGISTER_RETURNED awaitingCallback=true");
        } catch (RuntimeException | LinkageError e) {
            Log.e(TAG, "STEERING_SUBSCRIPTION_FAILED", e);
            observer.onSteering(null);
            stop();
        }
    }

    void stop() {
        active = false;
        if (steeringWheel != null && listener != null) {
            try {
                steeringWheel.unregisterSteeringWheelAngle(listener);
                Log.i(TAG, "STEERING_UNREGISTER_RETURNED");
            } catch (RuntimeException | LinkageError e) {
                Log.e(TAG, "STEERING_UNSUBSCRIBE_FAILED", e);
            }
        }
        listener = null;
        steeringWheel = null;
        if (vehicle != null) {
            try {
                vehicle.release();
                Log.i(TAG, "SDK_RELEASE_RETURNED");
            } catch (RuntimeException | LinkageError e) {
                Log.e(TAG, "SDK_RELEASE_FAILED", e);
            }
            vehicle = null;
        }
    }
}
