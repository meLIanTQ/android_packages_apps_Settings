/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.TextUtils;

import java.io.File;

/*
 * Displays preferences for application developers.
 */
public class DevelopmentSettings extends PreferenceActivity
        implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener, Preference.OnPreferenceChangeListener {

    private static final String ENABLE_ADB = "enable_adb";
    private static final String ADB_NOTIFY = "adb_notify";
    private static final String KEEP_SCREEN_ON = "keep_screen_on";
    private static final String ALLOW_MOCK_LOCATION = "allow_mock_location";
    private static final String KILL_APP_LONGPRESS_BACK = "kill_app_longpress_back";

    private CheckBoxPreference mEnableAdb;
    private CheckBoxPreference mAdbNotify;
    private CheckBoxPreference mKeepScreenOn;
    private CheckBoxPreference mAllowMockLocation;
    private CheckBoxPreference mKillAppLongpressBack;

    // To track whether Yes was clicked in the adb warning dialog
    private boolean mOkClicked;

    private Dialog mOkDialog;

    private static final String COMPCACHE_PREF = "pref_compcache";
    private static final String COMPCACHE_PROP = "persist.service.compcache";
    private static final String JIT_PREF = "pref_jit_mode";
    private static final String JIT_ENABLED = "int:jit";
    private static final String JIT_DISABLED = "int:fast";
    private static final String JIT_PERSIST_PROP = "persist.sys.jit-mode";
    private static final String JIT_PROP = "dalvik.vm.execution-mode";
    private static final String HEAPSIZE_PREF = "pref_heapsize";
    private static final String HEAPSIZE_PROP = "dalvik.vm.heapsize";
    private static final String HEAPSIZE_PERSIST_PROP = "persist.sys.vm.heapsize";
    private static final String HEAPSIZE_DEFAULT = "16m";
    private static final String USE_DITHERING_PREF = "pref_use_dithering";
    private static final String USE_DITHERING_PERSIST_PROP = "persist.sys.use_dithering";
    private static final String USE_DITHERING_DEFAULT = "1";
    private static final String LOCK_HOME_PREF = "pref_lock_home";
    private static final int LOCK_HOME_DEFAULT = 0;

    private CheckBoxPreference mCompcachePref;
    private CheckBoxPreference mJitPref;
    private CheckBoxPreference mUseDitheringPref;
    private CheckBoxPreference mLockHomePref;

    private ListPreference mHeapsizePref;

    private AlertDialog alertDialog;

    private int swapAvailable = -1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.development_prefs);

        PreferenceScreen prefSet = getPreferenceScreen();

        mEnableAdb = (CheckBoxPreference) findPreference(ENABLE_ADB);
        mAdbNotify = (CheckBoxPreference) findPreference(ADB_NOTIFY);
        mKeepScreenOn = (CheckBoxPreference) findPreference(KEEP_SCREEN_ON);
        mAllowMockLocation = (CheckBoxPreference) findPreference(ALLOW_MOCK_LOCATION);
        mKillAppLongpressBack = (CheckBoxPreference) findPreference(KILL_APP_LONGPRESS_BACK);

	// Performance Settings
        mCompcachePref = (CheckBoxPreference) prefSet.findPreference(COMPCACHE_PREF);
        if (isSwapAvailable()) {
            mCompcachePref.setChecked(SystemProperties.getBoolean(COMPCACHE_PROP, false));
        } else {
            prefSet.removePreference(mCompcachePref);
        }

        mJitPref = (CheckBoxPreference) prefSet.findPreference(JIT_PREF);
        String jitMode = SystemProperties.get(JIT_PERSIST_PROP,
                SystemProperties.get(JIT_PROP, JIT_ENABLED));
        mJitPref.setChecked(JIT_ENABLED.equals(jitMode));
        
        mUseDitheringPref = (CheckBoxPreference) prefSet.findPreference(USE_DITHERING_PREF);
        String useDithering = SystemProperties.get(USE_DITHERING_PERSIST_PROP, USE_DITHERING_DEFAULT);
        mUseDitheringPref.setChecked("1".equals(useDithering));
        
        mHeapsizePref = (ListPreference) prefSet.findPreference(HEAPSIZE_PREF);
        mHeapsizePref.setValue(SystemProperties.get(HEAPSIZE_PERSIST_PROP, 
                SystemProperties.get(HEAPSIZE_PROP, HEAPSIZE_DEFAULT)));
        mHeapsizePref.setOnPreferenceChangeListener(this);

        mLockHomePref = (CheckBoxPreference) prefSet.findPreference(LOCK_HOME_PREF);
        mLockHomePref.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.LOCK_HOME_IN_MEMORY, LOCK_HOME_DEFAULT) == 1);

        // Set up the warning
        alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(R.string.performance_settings_warning_title);
        alertDialog.setMessage(getResources().getString(R.string.performance_settings_warning));
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });
        
        alertDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mEnableAdb.setChecked(Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ADB_ENABLED, 0) != 0);
        
        mAdbNotify.setChecked(Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ADB_NOTIFY, 1) != 0);
                
        mKeepScreenOn.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.STAY_ON_WHILE_PLUGGED_IN, 0) != 0);
        mAllowMockLocation.setChecked(Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ALLOW_MOCK_LOCATION, 0) != 0);
        mKillAppLongpressBack.setChecked(Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.KILL_APP_LONGPRESS_BACK, 0) != 0);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (Utils.isMonkeyRunning()) {
            return false;
        }
        if (preference == mEnableAdb) {
            if (mEnableAdb.isChecked()) {
                mOkClicked = false;
                if (mOkDialog != null) dismissDialog();
                mOkDialog = new AlertDialog.Builder(this).setMessage(
                        getResources().getString(R.string.adb_warning_message))
                        .setTitle(R.string.adb_warning_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, this)
                        .setNegativeButton(android.R.string.no, this)
                        .show();
                mOkDialog.setOnDismissListener(this);
            } else {
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.ADB_ENABLED, 0);
            }
        } else if (preference == mAdbNotify) {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ADB_NOTIFY,
                    mAdbNotify.isChecked() ? 1 : 0);
        } else if (preference == mKeepScreenOn) {
            Settings.System.putInt(getContentResolver(), Settings.System.STAY_ON_WHILE_PLUGGED_IN, 
                    mKeepScreenOn.isChecked() ? 
                    (BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB) : 0);
        } else if (preference == mAllowMockLocation) {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION,
                    mAllowMockLocation.isChecked() ? 1 : 0);
        } else if (preference == mKillAppLongpressBack) {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.KILL_APP_LONGPRESS_BACK,
                    mKillAppLongpressBack.isChecked() ? 1 : 0);
        }
	if (preference == mCompcachePref) {
            SystemProperties.set(COMPCACHE_PROP, mCompcachePref.isChecked() ? "1" : "0");
            return true;
        }
        if (preference == mJitPref) {
            SystemProperties.set(JIT_PERSIST_PROP, 
                    mJitPref.isChecked() ? JIT_ENABLED : JIT_DISABLED);
            return true;
        }
        if (preference == mUseDitheringPref) {
            SystemProperties.set(USE_DITHERING_PERSIST_PROP,
                    mUseDitheringPref.isChecked() ? "1" : "0");
            return true;
        }
        if (preference == mLockHomePref) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.LOCK_HOME_IN_MEMORY, mLockHomePref.isChecked() ? 1 : 0);
            return true;
        }
        return false;
    }

    private void dismissDialog() {
        if (mOkDialog == null) return;
        mOkDialog.dismiss();
        mOkDialog = null;
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            mOkClicked = true;
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ADB_ENABLED, 1);
        } else {
            // Reset the toggle
            mEnableAdb.setChecked(false);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        // Assuming that onClick gets called first
        if (!mOkClicked) {
            mEnableAdb.setChecked(false);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHeapsizePref) {
            if (newValue != null) {
                SystemProperties.set(HEAPSIZE_PERSIST_PROP, (String)newValue);
                return true;
            }
        }
        return false;
    }

    /**
     * Check if swap support is available on the system
     */
    private boolean isSwapAvailable() {
        if (swapAvailable < 0) {
            swapAvailable = new File("/proc/swaps").exists() ? 1 : 0;
        }
        return swapAvailable > 0;
    }

    @Override
    public void onDestroy() {
        dismissDialog();
        super.onDestroy();
    }
}
