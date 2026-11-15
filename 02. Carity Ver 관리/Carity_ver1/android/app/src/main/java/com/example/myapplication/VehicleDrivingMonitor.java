package com.example.myapplication;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Observes the platform policy. Never modifies restrictions or driving state. */
final class VehicleDrivingMonitor {
    interface Listener { void onRestrictions(boolean known, boolean requiresDo, int flags); }
    private final Context context;
    private final Listener listener;
    private Car car;
    private CarUxRestrictionsManager ux;
    private boolean active;
    private int generation;
    private final Handler main = new Handler(Looper.getMainLooper());

    VehicleDrivingMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (active) return;
        active = true;
        int session = ++generation;
        listener.onRestrictions(false, true, -1);
        try {
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                    (connected, ready) -> {
                        if (!active || generation != session) return;
                        if (!ready) {
                            ux = null;
                            listener.onRestrictions(false, true, -1);
                            Log.w("OnSensePleos", "UX_SERVICE_DISCONNECTED");
                            return;
                        }
                        try {
                            ux = (CarUxRestrictionsManager) connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                            if (ux == null) throw new IllegalStateException("No UX restriction service");
                            ux.registerListener(restrictions -> {
                                if (active && generation == session) publish(restrictions);
                            });
                            publish(ux.getCurrentCarUxRestrictions());

                        } catch (RuntimeException | LinkageError e) {
                            listener.onRestrictions(false, true, -1);
                            Log.e("OnSensePleos", "UX_MONITOR_FAILED", e);
                        }
                    });
            if (car == null) listener.onRestrictions(false, true, -1);
        } catch (RuntimeException | LinkageError e) {
            listener.onRestrictions(false, true, -1);
            Log.e("OnSensePleos", "UX_INITIALIZATION_FAILED", e);
        }
    }

    private void publish(CarUxRestrictions restrictions) {
        if (restrictions == null) {
            listener.onRestrictions(false, true, -1);
            return;
        }
        boolean requiresDo = restrictions.isRequiresDistractionOptimization();
        int flags = restrictions.getActiveRestrictions();
        Log.i("OnSensePleos", "UX_RESTRICTIONS requiresDO=" + requiresDo
                + " flags=0x" + Integer.toHexString(flags));
        listener.onRestrictions(true, requiresDo, flags);
    }

    void stop() {
        active = false;
        generation++;
        try { if (ux != null) ux.unregisterListener(); }
        catch (RuntimeException e) { Log.e("OnSensePleos", "UX_UNREGISTER_FAILED", e); }
        ux = null;
        if (car != null) {
            try { car.disconnect(); }
            catch (RuntimeException e) { Log.e("OnSensePleos", "UX_RELEASE_FAILED", e); }
            car = null;
        }
    }
}
