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

package com.atakmap.android.takcad.plugin;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.IncidentResponderManager;
import com.atakmap.android.takcad.plugin.R;
import com.atakmap.android.takcad.routing.DirectionsResponsePojos;
import com.atakmap.android.takcad.util.MiscUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IncidentRespondersListAdapter extends BaseExpandableListAdapter {
    private static final String TAG = IncidentRespondersListAdapter.class.getSimpleName();
    private Context context;
    private List<String> expandableTitleList;
    private Map<String, List<IncidentResponderManager.ResponderInfo>> responderInfoDetailList;

    public IncidentRespondersListAdapter(Context context, List<String> expandableTitleList,
                                         Map<String, List<IncidentResponderManager.ResponderInfo>> responderInfoDetailList) {
        this.context = context;
        this.expandableTitleList = expandableTitleList;
        this.responderInfoDetailList = responderInfoDetailList;
    }

    /**
     * Get the data associated with a child within a given group
     * @param listPos index into the main list
     * @param expandedListPos index into the sublist
     * @return the data
     */
    @Override
    public Object getChild(int listPos, int expandedListPos) {
        String title = expandableTitleList.get(listPos);
        if (title == null) {
            return null;
        }
        List<IncidentResponderManager.ResponderInfo> subItems = responderInfoDetailList.get(title);
        if (subItems == null) {
            return null;
        } else {
            IncidentResponderManager.ResponderInfo responderInfo = subItems.get(expandedListPos);
            DirectionsResponsePojos.Feature feature = responderInfo.directions.features.get(0);
            String durationString = MiscUtils.convertMinutesToTimeString(MiscUtils.convertSecondsToMinutes(feature.properties.summary.duration));
            return responderInfo.callSign + "\n" +
                    "ETA: " + durationString + "\n" +
                    "Distance: " + feature.properties.summary.distance + " m";
        }
    }

    /**
     * Get the id associated with a child within a given group
     * @param listPos index into the main list
     * @param expandedListPos index into the sublist
     * @return the data
     */
    @Override
    public long getChildId(int listPos, int expandedListPos) {
        Log.d(TAG, "getChildId: expandedListPos=" + expandedListPos);
        return expandedListPos;
    }

    /**
     * Get a View that displays the given child within the given group
     * @param listPos index into the main list
     * @param expandedListPos index into the sublist
     * @param isLastChild
     * @param convertView the list item
     * @param parent
     * @return
     */
    @Override
    public View getChildView(int listPos, int expandedListPos,
                             boolean isLastChild, View convertView,
                             ViewGroup parent) {
        final String expandedListText = (String) getChild(listPos, expandedListPos);
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.incident_viewer_layout_list_item, null);
        }
        TextView expandedListTextView = (TextView) convertView.findViewById(R.id.expandedListItem);
        expandedListTextView.setText(expandedListText);

        return convertView;
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);

        String incidentId = expandableTitleList.get(groupPosition);

        IncidentResponderManager.getInstance().updateLastSelectedIncidentId(incidentId);

        IncidentResponderManager.IncidentInfo incidentInfo = IncidentResponderManager.getInstance().getIncidentInfo(incidentId);

        GeoPoint incidentPoint = new GeoPoint(Double.parseDouble(incidentInfo.latitude), Double.parseDouble(incidentInfo.longitude));

        MapView.getMapView().getMapController().panZoomTo(incidentPoint, .00001, true);

    }

    @Override
    public int getChildrenCount(int listPos) {
        String title = expandableTitleList.get(listPos);
        if (title == null) {
            return 0;
        }
        List<IncidentResponderManager.ResponderInfo> subItems = responderInfoDetailList.get(title);
        if (subItems == null) {
            return 0;
        }
        Log.d(TAG, "getChildrenCount: pos=" + listPos + ", size=" + subItems.size());
        return subItems.size();
    }

    @Override
    public Object getGroup(int listPos) {
        Log.d(TAG, "getGroup: " + listPos);
        return expandableTitleList.get(listPos);
    }

    @Override
    public int getGroupCount() {
        Log.d(TAG, "getGroupCount: " + expandableTitleList.size());
        return expandableTitleList.size();
    }

    @Override
    public long getGroupId(int listPos) {
        Log.d(TAG, "getGroupId: " + listPos);
        return listPos;
    }

    @Override
    public View getGroupView(int listPos, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        Log.d(TAG, "getGroupView: listPos=" + listPos + ", isExpanded=" + isExpanded);
        String incidentId = (String) getGroup(listPos);
        Log.d(TAG, "incidentId: " + incidentId);
        IncidentResponderManager.IncidentInfo incidentInfo = IncidentResponderManager.getInstance().getIncidentInfo(incidentId);
        String listTitle = "INCIDENT_INFO_NOT_FOUND";
        if (incidentInfo != null) {
            listTitle = incidentInfo.title;
        }
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) this.context.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.incident_viewer_layout_list_group, null);
        }
        TextView listTitleTextView = (TextView) convertView.findViewById(R.id.list_title);
        listTitleTextView.setTypeface(null, Typeface.BOLD);
        listTitleTextView.setText(listTitle);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int listPos, int expandedListPos) {
        return true;
    }
}
