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

package com.atakmap.android.takcad.receivers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.takcad.IncidentResponderManager;
import com.atakmap.android.takcad.plugin.R;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.TabbedDropDownReceiver;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MainDropDownReceiver displays a menu containing information about the current missions,
 * as well as the next task and its due date, among other things
 */
public class ActiveIncidentsDropDownReceiver extends TabbedDropDownReceiver implements DropDown.OnStateListener {

    public static final String TAG = ActiveIncidentsDropDownReceiver.class.getSimpleName();

    public static final String SHOW_INCIDENT_VIEWER = ActiveIncidentsDropDownReceiver.class.getSimpleName() + "SHOW_INCIDENT_VIEWER";
    private final View templateView;

    private List<String> expandableTitleList = null;
    Map<String, List<IncidentResponderManager.ResponderInfo>> expandableDetailList;
    IncidentRespondersListAdapter expandableListAdapter;

    private Handler handler;

    private static final int MSG_UPDATE_LIST = 0;
    private static final int UPDATE_LIST_INTERVAL = 500;

    /**************************** CONSTRUCTOR *****************************/

    @SuppressLint("MissingPermission")
    public ActiveIncidentsDropDownReceiver(final MapView mapView,
                                           final Context context) {
        super(mapView);

        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.incident_viewer_layout, null);

        LinearLayout templateViewLayout = (LinearLayout) templateView;

        ExpandableListView expandableListView = (ExpandableListView) templateView.findViewById(
                R.id.expandable_list_view);

        Set<String> incidentIdList = IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo().keySet();

        expandableTitleList = new ArrayList<>(incidentIdList);
        expandableDetailList = IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo();

        expandableListAdapter = new IncidentRespondersListAdapter(context,
                expandableTitleList, expandableDetailList);
        expandableListView.setAdapter(expandableListAdapter);

        setupTabs(context, templateView);

        handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_LIST: {

                        updateList();

                        Message updateListMsg = new Message();
                        updateListMsg.what = MSG_UPDATE_LIST;
                        handler.sendMessageDelayed(updateListMsg, UPDATE_LIST_INTERVAL);

                        break;
                    }
                }

                return true;
            }
        });

        Message updateListMsg = new Message();
        updateListMsg.what = MSG_UPDATE_LIST;
        handler.sendMessageDelayed(updateListMsg, UPDATE_LIST_INTERVAL);

    }

    @Override
    protected void disposeImpl() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "IncidentCreatorDropDownReceiver invoked");

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_INCIDENT_VIEWER)) {

            selectTab(templateView, ownedTabIndex());

            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

        }
    }

    private void updateList() {
        expandableTitleList.clear();
        Set<String> incidentIdList = IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo().keySet();
        expandableTitleList.addAll(incidentIdList);

        expandableDetailList.clear();
        expandableDetailList.putAll(IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo());

        expandableListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDropDownSelectionRemoved() {

    }

    @Override
    public void onDropDownClose() {

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {

    }

    @Override
    public void onDropDownVisible(boolean v) {

    }

    @Override
    protected int ownedTabIndex() {
        return 1;
    }
}