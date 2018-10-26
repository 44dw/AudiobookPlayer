package com.a44dw.audiobookplayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

public class Preferences extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener,
                                                                     MainActivity.OnBackPressedListener {

    OnIterationWithActivityListener activityListener;

    public static final String KEY_REWIND_RIGHT = "rewindRight";
    public static final String KEY_REWIND_LEFT = "rewindLeft";
    public static final String TO_CHAPTER_END = "toChapterEnd";
    public static final String REMAIN_TIME = "remainTime";

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences);
        PreferenceManager.setDefaultValues(getContext(), R.xml.preferences,false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activityListener = (OnIterationWithActivityListener) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(MainActivity.TAG, "Preferences -> onSharedPreferenceChanged");
        Preference pref = findPreference(key);

        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activityListener = null;
    }

    @Override
    public void onBackPressed() {
        activityListener.goBack();
    }
}
