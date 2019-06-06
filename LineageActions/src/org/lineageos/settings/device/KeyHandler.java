/*
 * Copyright (C) 2014 Slimroms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.settings.device;

import android.database.ContentObserver;
import android.content.BroadcastReceiver;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.service.notification.ZenModeConfig;
import org.lineageos.settings.device.settings.ScreenOffGesture;
import android.os.UserHandle;
import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;
import org.lineageos.settings.device.util.ActionConstants;
import org.lineageos.settings.device.util.Action;
import org.lineageos.settings.device.util.Utils;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;
    private static final int GESTURE_WAKELOCK_DURATION = 2000;
    private static final boolean DEBUG = true;

    // Supported scancodes
    private static final int GESTURE_DOUBLE_TAP_SCANCODE = 260;
    private static final int GESTURE_C_SCANCODE = 249;
    private static final int GESTURE_E_SCANCODE = 250;
    private static final int GESTURE_S_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_W_SCANCODE = 253;
    private static final int GESTURE_Z_SCANCODE = 254;
    private static final int GESTURE_SWIPE_UP_SCANCODE = 255;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 256;
    private static final int GESTURE_SWIPE_LEFT_SCANCODE = 257;
    private static final int GESTURE_SWIPE_RIGHT_SCANCODE = 258;

     private static final int[] sSupportedGestures = new int[]{
        GESTURE_DOUBLE_TAP_SCANCODE,
        GESTURE_C_SCANCODE,
        GESTURE_E_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_W_SCANCODE,
        GESTURE_S_SCANCODE,
        GESTURE_Z_SCANCODE,
        GESTURE_SWIPE_UP_SCANCODE,
	GESTURE_SWIPE_DOWN_SCANCODE,
	GESTURE_SWIPE_LEFT_SCANCODE,
	GESTURE_SWIPE_RIGHT_SCANCODE
    };

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final PowerManager mPowerManager;
    private final NotificationManager mNoMan;
    private Context mGestureContext = null;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;
    private WakeLock mGestureWakeLock;
    private Handler mHandler;
    private int mCurrentPosition;
    private boolean mUseProxiCheck;
    private Sensor mProximitySensor;
    private SettingsObserver mSettingsObserver;

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent) {
         }
   };

   private Intent createIntent(String value) {
        ComponentName componentName = ComponentName.unflattenFromString(value);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.setComponent(componentName);
        return intent;
}

    public KeyHandler(Context context) {
        mContext = context;
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        mEventHandler = new EventHandler();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mNoMan = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mSensorManager = context.getSystemService(SensorManager.class);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mScreenStateReceiver, screenStateFilter);
        mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ProximityWakeLock");
        mHandler = new Handler(); 
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        try {
            mGestureContext = mContext.createPackageContext(
                    "org.lineageos.settings.device", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
        }
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            String action = null;
            switch(event.getScanCode()) {
            case GESTURE_DOUBLE_TAP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOUBLE_TAP,
                        ActionConstants.ACTION_WAKE_DEVICE);
                        doHapticFeedback();
                break;

            case GESTURE_C_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_C,
                        ActionConstants.ACTION_CAMERA);
                        doHapticFeedback();
                break;
            case GESTURE_E_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_E,
                        ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
                        doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_V,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_W_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_W,
                        ActionConstants.ACTION_TORCH);
                        doHapticFeedback();
                break;
            case GESTURE_S_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_S,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_Z_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_Z,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
			case GESTURE_SWIPE_UP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_UP,
                        ActionConstants.ACTION_WAKE_DEVICE);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOWN,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_LEFT_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_LEFT,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_RIGHT_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_RIGHT,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
    }

            if (action == null || action != null && action.equals(ActionConstants.ACTION_NULL)) {
                return;
            }
            if (action.equals(ActionConstants.ACTION_CAMERA)
                    || !action.startsWith("**")) {
                Action.processAction(mContext, ActionConstants.ACTION_WAKE_DEVICE, false);
            }
            Action.processAction(mContext, action, false);
        }
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
        }
}

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
        boolean enabled = getGestureSharedPreferences().getInt(Utils.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1) != 0;
        if (enabled) {
            mVibrator.vibrate(50);
        }
    }

    private SharedPreferences getGestureSharedPreferences() {
        return mGestureContext.getSharedPreferences(
                Utils.PREFERENCES,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return event;
        }
        int scanCode = event.getScanCode();
        if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
            Message msg = getMessageForKeyEvent(event);
            if (mProximitySensor != null) {
                mEventHandler.sendMessageDelayed(msg, 200);
                processEvent(event);
            } else {
                mEventHandler.sendMessage(msg);
            }
        }

        return event;
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }

    private void processEvent(final KeyEvent keyEvent) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(keyEvent);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

}
