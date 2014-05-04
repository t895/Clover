/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.fragment;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.ui.activity.AboutActivity;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment {
    private int clickCount = 0;

    private Preference developerPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference);

        Preference aboutLicences = findPreference("about_licences");
        if (aboutLicences != null) {
            aboutLicences.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), AboutActivity.class));

                    return true;
                }
            });
        }

        Preference aboutVersion = findPreference("about_version");
        if (aboutVersion != null) {
            aboutVersion.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (++clickCount >= 5) {
                        clickCount = 0;

                        boolean enabled = !ChanPreferences.getDeveloper();
                        ChanPreferences.setDeveloper(enabled);
                        updateDeveloperPreference();

                        Toast.makeText(getActivity(), (enabled ? "Enabled " : "Disabled ") + "developer options",
                                Toast.LENGTH_LONG).show();
                    }

                    return true;
                }
            });

            String version = "";
            try {
                version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }

            aboutVersion.setTitle(R.string.app_name);
            aboutVersion.setSummary(version);
        }

        developerPreference = findPreference("about_developer");
        ((PreferenceGroup) findPreference("group_about")).removePreference(developerPreference);
        updateDeveloperPreference();
    }

    @Override
    public void onResume() {
        super.onResume();

        final Preference watchPreference = findPreference("watch_settings");
        if (watchPreference != null) {
            watchPreference.setSummary(ChanPreferences.getWatchEnabled() ? R.string.watch_summary_enabled
                    : R.string.watch_summary_disabled);
        }

        final Preference passPreference = findPreference("pass_settings");
        if (passPreference != null) {
            passPreference.setSummary(ChanPreferences.getPassEnabled() ? R.string.pass_summary_enabled
                    : R.string.pass_summary_disabled);
        }
    }

    private void updateDeveloperPreference() {
        if (ChanPreferences.getDeveloper()) {
            ((PreferenceGroup) findPreference("group_about")).addPreference(developerPreference);
        } else {
            ((PreferenceGroup) findPreference("group_about")).removePreference(developerPreference);
        }
    }
}
