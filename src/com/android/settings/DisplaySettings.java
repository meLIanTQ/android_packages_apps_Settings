/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import java.util.ArrayList;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.IWindowManager;

public class DisplaySettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String BACKLIGHT_SETTINGS = "backlight_settings";

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ANIMATIONS = "animations";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String ROTATION_90_PREF = "pref_rotation_90";
    private static final String ROTATION_180_PREF = "pref_rotation_180";
    private static final String ROTATION_270_PREF = "pref_rotation_270";

    private static final String UI_EXP_WIDGET = "expanded_widget";
    private static final String UI_EXP_WIDGET_COLOR = "expanded_color_mask";
    private static final String UI_EXP_WIDGET_PICKER = "widget_picker";

    private PreferenceScreen mBacklightScreen;

    private ListPreference mAnimations;

    private CheckBoxPreference mAccelerometer;
    private CheckBoxPreference mRotation90Pref;
    private CheckBoxPreference mRotation180Pref;
    private CheckBoxPreference mRotation270Pref;

    private CheckBoxPreference mPowerWidget;
    private Preference mPowerWidgetColor;
    private PreferenceScreen mPowerPicker;

    private float[] mAnimationScales;

    private IWindowManager mWindowManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getContentResolver();
        mWindowManager = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));

        addPreferencesFromResource(R.xml.display_settings);
	PreferenceScreen prefSet = getPreferenceScreen();

	mBacklightScreen = (PreferenceScreen) prefSet.findPreference(BACKLIGHT_SETTINGS);
        // No reason to show backlight if no light sensor on device
        if (((SensorManager)getSystemService(SENSOR_SERVICE)).getDefaultSensor(
            Sensor.TYPE_LIGHT) == null) {
            ((PreferenceCategory)prefSet.findPreference(BACKLIGHT_SETTINGS)).removePreference(mBacklightScreen);
        }

        mAnimations = (ListPreference) findPreference(KEY_ANIMATIONS);
        mAnimations.setOnPreferenceChangeListener(this);
        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);

        /* Rotation */
        mRotation90Pref = (CheckBoxPreference) prefSet.findPreference(ROTATION_90_PREF);
        mRotation180Pref = (CheckBoxPreference) prefSet.findPreference(ROTATION_180_PREF);
        mRotation270Pref = (CheckBoxPreference) prefSet.findPreference(ROTATION_270_PREF);
        int mode = Settings.System.getInt(getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION_MODE, 5);
        mRotation90Pref.setChecked((mode & 1) != 0);
        mRotation180Pref.setChecked((mode & 2) != 0);
        mRotation270Pref.setChecked((mode & 4) != 0);

        ListPreference screenTimeoutPreference =
            (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        screenTimeoutPreference.setValue(String.valueOf(Settings.System.getInt(
                resolver, SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE)));
        screenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(screenTimeoutPreference);

        /* Expanded View Power Widget */
        mPowerWidget = (CheckBoxPreference) prefSet.findPreference(UI_EXP_WIDGET);
        mPowerWidgetColor = prefSet.findPreference(UI_EXP_WIDGET_COLOR);
        mPowerPicker = (PreferenceScreen)prefSet.findPreference(UI_EXP_WIDGET_PICKER);

    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
            (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.valueOf(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            final int userPreference = Integer.valueOf(screenTimeoutPreference.getValue());
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateState(true);
    }

    private void updateState(boolean force) {
        int animations = 0;
        try {
            mAnimationScales = mWindowManager.getAnimationScales();
        } catch (RemoteException e) {
        }
        if (mAnimationScales != null) {
            if (mAnimationScales.length >= 1) {
                animations = ((int)(mAnimationScales[0]+.5f)) % 10;
            }
            if (mAnimationScales.length >= 2) {
                animations += (((int)(mAnimationScales[1]+.5f)) & 0x7) * 10;
            }
        }
        int idx = 0;
        int best = 0;
        CharSequence[] aents = mAnimations.getEntryValues();
        for (int i=0; i<aents.length; i++) {
            int val = Integer.parseInt(aents[i].toString());
            if (val <= animations && val > best) {
                best = val;
                idx = i;
            }
        }
        mAnimations.setValueIndex(idx);
        updateAnimationsSummary(mAnimations.getValue());
        mAccelerometer.setChecked(Settings.System.getInt(
                getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) != 0);
    }

    private void updateAnimationsSummary(Object value) {
        CharSequence[] summaries = getResources().getTextArray(R.array.animations_summaries);
        CharSequence[] values = mAnimations.getEntryValues();
        for (int i=0; i<values.length; i++) {
            //Log.i("foo", "Comparing entry "+ values[i] + " to current "
            //        + mAnimations.getValue());
            if (values[i].equals(value)) {
                mAnimations.setSummary(summaries[i]);
                break;
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mBacklightScreen) {
	    startActivity(mBacklightScreen.getIntent());
        }
        if (preference == mAccelerometer) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION,
                    mAccelerometer.isChecked() ? 1 : 0);
        }
        if (preference == mRotation90Pref ||
            preference == mRotation180Pref ||
            preference == mRotation270Pref) {
            int mode = 0;
            if (mRotation90Pref.isChecked()) mode |= 1;
            if (mRotation180Pref.isChecked()) mode |= 2;
            if (mRotation270Pref.isChecked()) mode |= 4;
            Settings.System.putInt(getContentResolver(),
                     Settings.System.ACCELEROMETER_ROTATION_MODE, mode);
        }
        if(preference == mPowerPicker) {
            startActivity(mPowerPicker.getIntent());
        }
        if(preference == mPowerWidget) {
            value = mPowerWidget.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.EXPANDED_VIEW_WIDGET, value ? 1 : 0);
        }

        if (preference == mPowerWidgetColor) {
            ColorPickerDialog cp = new ColorPickerDialog(this,
                mWidgetColorListener,
                readWidgetColor());
            cp.show();
        }
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_ANIMATIONS.equals(key)) {
            try {
                int value = Integer.parseInt((String) objValue);
                if (mAnimationScales.length >= 1) {
                    mAnimationScales[0] = value%10;
                }
                if (mAnimationScales.length >= 2) {
                    mAnimationScales[1] = (value/10)%10;
                }
                try {
                    mWindowManager.setAnimationScales(mAnimationScales);
                } catch (RemoteException e) {
                }
                updateAnimationsSummary(objValue);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist animation setting", e);
            }

        }
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(),
                        SCREEN_OFF_TIMEOUT, value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }

        return true;
    }

    private int readWidgetColor() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.EXPANDED_VIEW_WIDGET_COLOR);
        }
        catch (SettingNotFoundException e) {
            return -16777216;
        }
    }
    ColorPickerDialog.OnColorChangedListener mWidgetColorListener =
        new ColorPickerDialog.OnColorChangedListener() {
            public void colorChanged(int color) {
                Settings.System.putInt(getContentResolver(), Settings.System.EXPANDED_VIEW_WIDGET_COLOR, color);
            }
            public void colorUpdate(int color) {
            }
    };

}
