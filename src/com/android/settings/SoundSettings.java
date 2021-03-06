/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.IWindowManager;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import java.util.Calendar;

public class SoundSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "SoundAndDisplaysSettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
    private static final int FALLBACK_EMERGENCY_TONE_VALUE = 0;

    private static final String KEY_SILENT = "silent";
    private static final String KEY_VIBRATE = "vibrate";
    private static final String KEY_VOLUME_CONTROL_SILENT = "volume_control_silent";
    private static final String KEY_DTMF_TONE = "dtmf_tone";
    private static final String KEY_SOUND_EFFECTS = "sound_effects";
    private static final String KEY_EMERGENCY_TONE = "emergency_tone";
    private static final String KEY_SOUND_SETTINGS = "sound_settings";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_NOTIFICATION_BLINK = "notification_blink";
    private static final String KEY_NOTIFICATION_ALWAYS_ON = "notification_always_on";
    private static final String KEY_NOTIFICATION_CHARGING = "notification_charging";
    private static final String KEY_LOCK_SOUNDS = "lock_sounds";

    private static final String VALUE_VIBRATE_NEVER = "never";
    private static final String VALUE_VIBRATE_ALWAYS = "always";
    private static final String VALUE_VIBRATE_ONLY_SILENT = "silent";
    private static final String VALUE_VIBRATE_UNLESS_SILENT = "notsilent";

    private static final String NOTIFICATIONS_FOCUS = "notif-focus";
    private static final String NOTIFICATIONS_SPEAKER = "notif-speaker";
    private static final String NOTIFICATIONS_ATTENUATION = "notif-attn";
    private static final String NOTIFICATIONS_LIMITVOL = "notif-limitvol";
    private static final String RINGS_SPEAKER = "ring-speaker";
    private static final String RINGS_ATTENUATION = "ring-attn";
    private static final String RINGS_LIMITVOL = "ring-limitvol";
    private static final String ALARMS_SPEAKER = "alarm-speaker";
    private static final String ALARMS_ATTENUATION = "alarm-attn";
    private static final String ALARMS_LIMITVOL = "alarm-limitvol";
    private static final int DIALOG_QUIET_HOURS_START = 1;
    private static final int DIALOG_QUIET_HOURS_END = 2;
    private static final String KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled";
    private static final String KEY_QUIET_HOURS_START = "quiet_hours_start";
    private static final String KEY_QUIET_HOURS_END = "quiet_hours_end";
    private static final String KEY_QUIET_HOURS_MUTE = "quiet_hours_mute";
    private static final String KEY_QUIET_HOURS_STILL = "quiet_hours_still";
    private static final String KEY_QUIET_HOURS_DIM = "quiet_hours_dim";

    private static final String PREFIX = "persist.sys.";

    private static final String HAPTIC_SETTINGS = "haptic_settings";

    private CheckBoxPreference mSilent;
    private CheckBoxPreference mVolumeControlSilent;
    private CheckBoxPreference mQuietHoursEnabled;
    private Preference mQuietHoursStart;
    private Preference mQuietHoursEnd;
    private PreferenceScreen mHapticScreen;
    private CheckBoxPreference mQuietHoursMute;
    private CheckBoxPreference mQuietHoursStill;
    private CheckBoxPreference mQuietHoursDim;

    private static String getKey(String suffix) {
        return PREFIX + suffix;
    }

    /*
     * If we are currently in one of the silent modes (the ringer mode is set to either
     * "silent mode" or "vibrate mode"), then toggling the "Phone vibrate"
     * preference will switch between "silent mode" and "vibrate mode".
     * Otherwise, it will adjust the normal ringer mode's ring or ring+vibrate
     * setting.
     */
    private ListPreference mVibrate;
    private CheckBoxPreference mDtmfTone;
    private CheckBoxPreference mSoundEffects;
    private CheckBoxPreference mNotificationPulse;
    private CheckBoxPreference mNotificationBlink;
    private CheckBoxPreference mNotificationAlwaysOn;
    private CheckBoxPreference mNotificationCharging;
    private CheckBoxPreference mLockSounds;

    private AudioManager mAudioManager;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                updateState(false);
            }
        }
    };

    private PreferenceGroup mSoundSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getContentResolver();
        int activePhoneType = TelephonyManager.getDefault().getPhoneType();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        addPreferencesFromResource(R.xml.sound_settings);

        PreferenceScreen prefSet = getPreferenceScreen();

        if (TelephonyManager.PHONE_TYPE_CDMA != activePhoneType) {
            // device is not CDMA, do not display CDMA emergency_tone
            getPreferenceScreen().removePreference(findPreference(KEY_EMERGENCY_TONE));
        }

        mSilent = (CheckBoxPreference) findPreference(KEY_SILENT);

        mVibrate = (ListPreference) findPreference(KEY_VIBRATE);
        mVibrate.setOnPreferenceChangeListener(this);

        CheckBoxPreference p = (CheckBoxPreference) prefSet.findPreference(NOTIFICATIONS_FOCUS);
        p.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.NOTIFICATIONS_AUDIO_FOCUS, 1) != 0);
        p.setOnPreferenceChangeListener(this);

        p = (CheckBoxPreference) prefSet.findPreference(NOTIFICATIONS_SPEAKER);
        p.setChecked(SystemProperties.getBoolean(getKey(NOTIFICATIONS_SPEAKER), false));
        p.setOnPreferenceChangeListener(this);

        p = (CheckBoxPreference) prefSet.findPreference(RINGS_SPEAKER);
        p.setChecked(SystemProperties.getBoolean(getKey(RINGS_SPEAKER), false));
        p.setOnPreferenceChangeListener(this);

        p = (CheckBoxPreference) prefSet.findPreference(ALARMS_SPEAKER);
        p.setChecked(SystemProperties.getBoolean(getKey(ALARMS_SPEAKER), false));
        p.setOnPreferenceChangeListener(this);

        ListPreference lp = (ListPreference) prefSet.findPreference(NOTIFICATIONS_ATTENUATION);
        lp.setValue(String.valueOf(SystemProperties.getInt(getKey(NOTIFICATIONS_ATTENUATION), 6)));
        lp.setSummary(lp.getEntry());
        lp.setOnPreferenceChangeListener(this);

        lp = (ListPreference) prefSet.findPreference(RINGS_ATTENUATION);
        lp.setValue(String.valueOf(SystemProperties.getInt(getKey(RINGS_ATTENUATION), 6)));
        lp.setSummary(lp.getEntry());
        lp.setOnPreferenceChangeListener(this);

        lp = (ListPreference) prefSet.findPreference(ALARMS_ATTENUATION);
        lp.setValue(String.valueOf(SystemProperties.getInt(getKey(ALARMS_ATTENUATION), 6)));
        lp.setSummary(lp.getEntry());
        lp.setOnPreferenceChangeListener(this);

        lp = (ListPreference) prefSet.findPreference(NOTIFICATIONS_LIMITVOL);
        lp.setValue(String.valueOf(SystemProperties.getInt(getKey(NOTIFICATIONS_LIMITVOL), 1)));
        lp.setSummary(lp.getEntry());
        lp.setOnPreferenceChangeListener(this);

        lp = (ListPreference) prefSet.findPreference(RINGS_LIMITVOL);
        lp.setValue(String.valueOf(SystemProperties.getInt(getKey(RINGS_LIMITVOL), 1)));
        lp.setSummary(lp.getEntry());
        lp.setOnPreferenceChangeListener(this);

        lp = (ListPreference) prefSet.findPreference(ALARMS_LIMITVOL);
        lp.setValue(String.valueOf(SystemProperties.getInt(getKey(ALARMS_LIMITVOL), 1)));
        lp.setSummary(lp.getEntry());
        lp.setOnPreferenceChangeListener(this);

        mQuietHoursEnabled = (CheckBoxPreference) findPreference(KEY_QUIET_HOURS_ENABLED);
        mQuietHoursStart = findPreference(KEY_QUIET_HOURS_START);
        mQuietHoursEnd = findPreference(KEY_QUIET_HOURS_END);
        mQuietHoursMute = (CheckBoxPreference) findPreference(KEY_QUIET_HOURS_MUTE);
        mQuietHoursStill = (CheckBoxPreference) findPreference(KEY_QUIET_HOURS_STILL);
        mQuietHoursDim = (CheckBoxPreference) findPreference(KEY_QUIET_HOURS_DIM);

        mVolumeControlSilent = (CheckBoxPreference)
                findPreference(KEY_VOLUME_CONTROL_SILENT);
        mVolumeControlSilent.setChecked(Settings.System.getInt(resolver,
                Settings.System.VOLUME_CONTROL_SILENT, 0) == 1);

        mDtmfTone = (CheckBoxPreference) findPreference(KEY_DTMF_TONE);
        mDtmfTone.setPersistent(false);
        mDtmfTone.setChecked(Settings.System.getInt(resolver,
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) != 0);
        mSoundEffects = (CheckBoxPreference) findPreference(KEY_SOUND_EFFECTS);
        mSoundEffects.setPersistent(false);
        mSoundEffects.setChecked(Settings.System.getInt(resolver,
                Settings.System.SOUND_EFFECTS_ENABLED, 0) != 0);

	mHapticScreen = (PreferenceScreen) findPreference(HAPTIC_SETTINGS);

        mLockSounds = (CheckBoxPreference) findPreference(KEY_LOCK_SOUNDS);
        mLockSounds.setPersistent(false);
        mLockSounds.setChecked(Settings.System.getInt(resolver,
                Settings.System.LOCKSCREEN_SOUNDS_ENABLED, 1) != 0);

        if (TelephonyManager.PHONE_TYPE_CDMA == activePhoneType) {
            ListPreference emergencyTonePreference =
                (ListPreference) findPreference(KEY_EMERGENCY_TONE);
            emergencyTonePreference.setValue(String.valueOf(Settings.System.getInt(
                resolver, Settings.System.EMERGENCY_TONE, FALLBACK_EMERGENCY_TONE_VALUE)));
            emergencyTonePreference.setOnPreferenceChangeListener(this);
        }

        mSoundSettings = (PreferenceGroup) findPreference(KEY_SOUND_SETTINGS);
        mNotificationPulse = (CheckBoxPreference)
                mSoundSettings.findPreference(KEY_NOTIFICATION_PULSE);
        mNotificationBlink = (CheckBoxPreference)
                mSoundSettings.findPreference(KEY_NOTIFICATION_BLINK);
        mNotificationAlwaysOn = (CheckBoxPreference)
                mSoundSettings.findPreference(KEY_NOTIFICATION_ALWAYS_ON);
        mNotificationCharging = (CheckBoxPreference)
                mSoundSettings.findPreference(KEY_NOTIFICATION_CHARGING);

        boolean amberGreenLight = getResources().getBoolean(
                com.android.internal.R.bool.config_amber_green_light);

        if (amberGreenLight) {
            mSoundSettings.removePreference(mNotificationPulse);
            mNotificationBlink.setChecked(Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_BLINK, 1) == 1);
            mNotificationBlink.setOnPreferenceChangeListener(this);

            mNotificationAlwaysOn.setChecked(Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_ALWAYS_ON, 1) == 1);
            mNotificationAlwaysOn.setOnPreferenceChangeListener(this);

            mNotificationCharging.setChecked(Settings.System.getInt(resolver,
                    Settings.System.NOTIFICATION_LIGHT_CHARGING, 1) == 1);
            mNotificationCharging.setOnPreferenceChangeListener(this);

        } else {
            mSoundSettings.removePreference(mNotificationBlink);
            mSoundSettings.removePreference(mNotificationAlwaysOn);
            mSoundSettings.removePreference(mNotificationCharging);

            if (mNotificationPulse != null &&
                    getResources().getBoolean(R.bool.has_intrusive_led) == false) {
                mSoundSettings.removePreference(mNotificationPulse);
            } else {
                try {
                    mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                            Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                    mNotificationPulse.setOnPreferenceChangeListener(this);
                } catch (SettingNotFoundException snfe) {
                    Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
                }
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        updateState(true);

        IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(mReceiver);
    }

    private String getPhoneVibrateSettingValue() {
        boolean vibeInSilent = (Settings.System.getInt(
            getContentResolver(),
            Settings.System.VIBRATE_IN_SILENT,
            1) == 1);

        // Control phone vibe independent of silent mode
        int callsVibrateSetting = 
            mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);

        if (vibeInSilent) {
            if (callsVibrateSetting == AudioManager.VIBRATE_SETTING_OFF) {
                // this state does not make sense; fix it up for the user
                mAudioManager.setVibrateSetting(
                    AudioManager.VIBRATE_TYPE_RINGER,
                    AudioManager.VIBRATE_SETTING_ONLY_SILENT);
            }
            if (callsVibrateSetting == AudioManager.VIBRATE_SETTING_ON) {
                return VALUE_VIBRATE_ALWAYS;
            } else {
                return VALUE_VIBRATE_ONLY_SILENT;
            }
        } else {
            if (callsVibrateSetting == AudioManager.VIBRATE_SETTING_ONLY_SILENT) {
                // this state does not make sense; fix it up
                mAudioManager.setVibrateSetting(
                    AudioManager.VIBRATE_TYPE_RINGER,
                    AudioManager.VIBRATE_SETTING_OFF);
            }
            if (callsVibrateSetting == AudioManager.VIBRATE_SETTING_ON) {
                return VALUE_VIBRATE_UNLESS_SILENT;
            } else {
                return VALUE_VIBRATE_NEVER;
            }
        }
    }

    private void setPhoneVibrateSettingValue(String value) {
        boolean vibeInSilent;
        int callsVibrateSetting;

        if (value.equals(VALUE_VIBRATE_UNLESS_SILENT)) {
            callsVibrateSetting = AudioManager.VIBRATE_SETTING_ON;
            vibeInSilent = false;
        } else if (value.equals(VALUE_VIBRATE_NEVER)) {
            callsVibrateSetting = AudioManager.VIBRATE_SETTING_OFF;
            vibeInSilent = false;
        } else if (value.equals(VALUE_VIBRATE_ONLY_SILENT)) {
            callsVibrateSetting = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
            vibeInSilent = true;
        } else { //VALUE_VIBRATE_ALWAYS
            callsVibrateSetting = AudioManager.VIBRATE_SETTING_ON;
            vibeInSilent = true;
        }

        Settings.System.putInt(getContentResolver(),
            Settings.System.VIBRATE_IN_SILENT,
            vibeInSilent ? 1 : 0);

        // might need to switch the ringer mode from one kind of "silent" to
        // another
        if (mSilent.isChecked()) {
            mAudioManager.setRingerMode(
                vibeInSilent ? AudioManager.RINGER_MODE_VIBRATE
                             : AudioManager.RINGER_MODE_SILENT);
        }

        mAudioManager.setVibrateSetting(
            AudioManager.VIBRATE_TYPE_RINGER,
            callsVibrateSetting);
    }

    // updateState in fact updates the UI to reflect the system state
    private void updateState(boolean force) {
        final int ringerMode = mAudioManager.getRingerMode();

        // NB: in the UI we now simply call this "silent mode". A separate
        // setting controls whether we're in RINGER_MODE_SILENT or
        // RINGER_MODE_VIBRATE.
        final boolean silentOrVibrateMode =
                ringerMode != AudioManager.RINGER_MODE_NORMAL;

        if (silentOrVibrateMode != mSilent.isChecked() || force) {
            mSilent.setChecked(silentOrVibrateMode);
        }

        String phoneVibrateSetting = getPhoneVibrateSettingValue();

        if (! phoneVibrateSetting.equals(mVibrate.getValue()) || force) {
            mVibrate.setValue(phoneVibrateSetting);
        }
        mVibrate.setSummary(mVibrate.getEntry());

        boolean vibeInSilent = (1 == Settings.System.getInt(getContentResolver(),
                                                            Settings.System.VIBRATE_IN_SILENT,1));
        mVolumeControlSilent.setEnabled(vibeInSilent);

        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);
        boolean isAlarmInclSilentMode = (silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0;
        mSilent.setSummary(isAlarmInclSilentMode ?
                R.string.silent_mode_incl_alarm_summary :
                R.string.silent_mode_summary);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSilent) {
            if (mSilent.isChecked()) {
                boolean vibeInSilent = (1 == Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.VIBRATE_IN_SILENT,
                    1));
                mAudioManager.setRingerMode(
                    vibeInSilent ? AudioManager.RINGER_MODE_VIBRATE
                                 : AudioManager.RINGER_MODE_SILENT);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
            updateState(false);
        } else if (preference == mQuietHoursEnabled) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.QUIET_HOURS_ENABLED,
                    mQuietHoursEnabled.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mQuietHoursMute) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.QUIET_HOURS_MUTE,
                    mQuietHoursMute.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mQuietHoursStill) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.QUIET_HOURS_STILL,
                    mQuietHoursStill.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mQuietHoursDim) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.QUIET_HOURS_DIM,
                    mQuietHoursDim.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mQuietHoursStart) {
            showDialog(DIALOG_QUIET_HOURS_START);
            return true;
        } else if (preference == mQuietHoursEnd) {
            showDialog(DIALOG_QUIET_HOURS_END);
            return true;
        } else if (preference == mVolumeControlSilent) {
            boolean value = mVolumeControlSilent.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.VOLUME_CONTROL_SILENT, value ? 1 : 0);
        } else if (preference == mDtmfTone) {
            Settings.System.putInt(getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING,
                    mDtmfTone.isChecked() ? 1 : 0);
        } else if (preference == mSoundEffects) {
            if (mSoundEffects.isChecked()) {
                mAudioManager.loadSoundEffects();
            } else {
                mAudioManager.unloadSoundEffects();
            }
            Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED,
                    mSoundEffects.isChecked() ? 1 : 0);
	} else if (preference == mLockSounds) {
            Settings.System.putInt(getContentResolver(), Settings.System.LOCKSCREEN_SOUNDS_ENABLED,
                    mLockSounds.isChecked() ? 1 : 0);

        } else if (preference == mNotificationPulse) {
            boolean value = mNotificationPulse.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_PULSE, value ? 1 : 0);
        } else if (preference == mNotificationBlink) {
            boolean value = mNotificationBlink.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_BLINK, value ? 1 : 0);

        } else if (preference == mNotificationAlwaysOn) {
            boolean value = mNotificationAlwaysOn.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_ALWAYS_ON, value ? 1 : 0);

        } else if (preference == mNotificationCharging) {
            boolean value = mNotificationCharging.isChecked();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.NOTIFICATION_LIGHT_CHARGING, value ? 1 : 0);
        }
	if (preference == mHapticScreen) {
	startActivity(mHapticScreen.getIntent());
        }
        return true;
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_EMERGENCY_TONE.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.EMERGENCY_TONE, value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist emergency tone setting", e);
            }
        }
	if (preference == mVibrate) {
            setPhoneVibrateSettingValue(objValue.toString());
            updateState(false);
        } else if (key.equals(NOTIFICATIONS_FOCUS)) {
            Settings.System.putInt(getContentResolver(),
                Settings.System.NOTIFICATIONS_AUDIO_FOCUS, getBoolean(objValue) ? 1 : 0);
        }else if (key.equals(NOTIFICATIONS_SPEAKER) ||
                key.equals(RINGS_SPEAKER) ||
                key.equals(ALARMS_SPEAKER)) {
            SystemProperties.set(getKey(key), getBoolean(objValue) ? "1" : "0");
        } else {
            SystemProperties.set(getKey(key), String.valueOf(getInt(objValue)));
            mHandler.sendMessage(mHandler.obtainMessage(0, key));
        }
        return true;
    }
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_QUIET_HOURS_START:
            return createTimePicker(Settings.System.QUIET_HOURS_START);
        case DIALOG_QUIET_HOURS_END:
            return createTimePicker(Settings.System.QUIET_HOURS_END);
        }
        return super.onCreateDialog(id);
    }

    private TimePickerDialog createTimePicker(final String key) {
        int value = Settings.System.getInt(getContentResolver(), key, -1);
        int hour;
        int minutes;
        if (value < 0) {
            Calendar calendar = Calendar.getInstance();
            hour = calendar.get(Calendar.HOUR_OF_DAY);
            minutes = calendar.get(Calendar.MINUTE);
        } else {
            hour = value / 60;
            minutes = value % 60;
        }
        TimePickerDialog dlg = new TimePickerDialog(
            this, /* context */
            new TimePickerDialog.OnTimeSetListener() {

                @Override
                public void onTimeSet(TimePicker v, int hours, int minutes) {
                    Settings.System.putInt(
                            getContentResolver(),
                            key,
                            hours * 60 + minutes
                    );
                };
            },
            hour,
            minutes,
            DateFormat.is24HourFormat(this)
        );
        return dlg;
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    if (msg.obj != null) {
                        ListPreference p = (ListPreference) findPreference(msg.obj.toString());
                        p.setSummary(p.getEntry());
                    }
                    break;
            }
        }
    };

    private boolean getBoolean(Object o) {
        return Boolean.valueOf(o.toString());
    }

    private int getInt(Object o) {
        return Integer.valueOf(o.toString());
    }

}
