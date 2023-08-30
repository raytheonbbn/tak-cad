/*
 *
 * TAK-CAD
 * Copyright (c) 2023 Raytheon Technologies
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
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 * https://github.com/atapas/add-copyright.git
 *
 */

package com.atakmap.android.takcad;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.atakmap.android.takcad.plugin.R;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.receivers.ReportIncidentDropDownReceiver;
import com.atakmap.android.takcad.receivers.ActiveIncidentsDropDownReceiver;
import com.atakmap.android.takcad.receivers.SettingsDropDownReceiver;
import com.atakmap.coremap.log.Log;
import com.google.android.material.tabs.TabLayout;

public abstract class TabbedDropDownReceiver extends DropDownReceiver {
    private static final String TAG = TabbedDropDownReceiver.class.getSimpleName();

    private TabLayout.OnTabSelectedListener listener = new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            Log.d(TAG, "onTagSelected: Position = " + tab.getPosition());
            Intent intent = new Intent();
            if (tab.getPosition()==ownedTabIndex()) {
                return;
            }
            switch (tab.getPosition()) {
                case 0:
                    Log.i(TAG, "INCIDENT CREATOR");
                    intent.setAction(ReportIncidentDropDownReceiver.SHOW_INCIDENT_CREATOR);
                    break;
                case 1:
                    Log.i(TAG, "INCIDENT VIEWER");
                    intent.setAction(ActiveIncidentsDropDownReceiver.SHOW_INCIDENT_VIEWER);
                    break;
                case 2:
                    Log.i(TAG, "SETTINGS");
                    intent.setAction(SettingsDropDownReceiver.SHOW_PLUGIN);
                    break;
                default:
                    Log.e(TAG, "Unexpected tab entry encountered");
            }
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }



        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
            // ** NO-OP
        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
            Log.d(TAG, "onTabReselected: Position = " + tab.getPosition());
            switch (tab.getPosition()) {
                case 0:
                    Log.v(TAG, "onTabReselected: INCIDENT CREATOR");
                    break;
                case 1:
                    Log.v(TAG, "onTabReselected: INCIDENT VIEWER");
                    break;
                case 2:
                    Log.v(TAG, "onTabReselected: SETTINGS");
                    break;
                default:
                    Log.e(TAG, "Unexpected tab entry encountered");
            }
        }
    };

    protected TabbedDropDownReceiver(MapView mapView) {
        super(mapView);
    }

    public void setupTabs(Context context, View mainView) {
        Log.d(TAG, "Adding a new on tab selected listener...");
        TabLayout tabs = mainView.findViewById(R.id.main_tab_layout);

        tabs.addOnTabSelectedListener(listener);
    }

    public void selectTab(View v, int index) {
        TabLayout tabs = v.findViewById(R.id.main_tab_layout);
        TabLayout.Tab tab = tabs.getTabAt(index);
        if (tab == null) {
            Log.e(TAG, "tab at index "+index+" was null");
            return;
        }
        tab.select();
    }

    public void removeTabsListener(View mainView) {
        TabLayout tabs = mainView.findViewById(R.id.main_tab_layout);
        tabs.removeOnTabSelectedListener(listener);
    }

    /**The index of the tab managed by this DropDownReceiver*/
    protected abstract int ownedTabIndex();

}
