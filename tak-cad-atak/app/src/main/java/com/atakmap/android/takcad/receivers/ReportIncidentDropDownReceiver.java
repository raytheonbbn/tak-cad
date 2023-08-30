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

import static android.graphics.Color.WHITE;
import static android.widget.LinearLayout.HORIZONTAL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.atakutils.MiscUtils;
import com.atakmap.android.gui.PluginSpinner;
import com.atakmap.android.takcad.plugin.R;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.takcad.Constants;
import com.atakmap.android.takcad.TabbedDropDownReceiver;
import com.atakmap.android.takcad.point_entry.persistence.MapStateListener;
import com.atakmap.android.takcad.point_entry.persistence.ShapeNameManager;
import com.atakmap.android.takcad.point_entry.shapes.PointCreatorCustom;
import com.atakmap.android.takcad.point_entry.shapes.ShapeCallback;
import com.atakmap.android.takcad.point_entry.shapes.ShapeCreator;
import com.atakmap.android.takcad.util.CotUtil;
import com.atakmap.android.takcad.point_entry.TextPrompt;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MainDropDownReceiver displays a menu containing information about the current missions,
 * as well as the next task and its due date, among other things
 */
public class ReportIncidentDropDownReceiver extends TabbedDropDownReceiver implements DropDown.OnStateListener {

    public static final String TAG = ReportIncidentDropDownReceiver.class.getSimpleName();

    public static final String SHOW_INCIDENT_CREATOR = ReportIncidentDropDownReceiver.class.getSimpleName() + "SHOW_INCIDENT_VIEWER";
    private final View templateView;

    private String latitude = "";
    private String longitude = "";
    private String title = "";
    private String summary = "";

    /**************************** CONSTRUCTOR *****************************/

    @SuppressLint("MissingPermission")
    public ReportIncidentDropDownReceiver(final MapView mapView,
                                          final Context context) {
        super(mapView);

        LinearLayout.LayoutParams layoutParamsButton = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                160);

        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.incident_creator_layout, null);

        LinearLayout templateViewLayout = templateView.findViewById(R.id.scrollLayout);

        TextView incidentTitle = new TextView(context);
        incidentTitle.setTextSize(18f);
        incidentTitle.setTextColor(WHITE);
        incidentTitle.setText(R.string.incident_title);
        EditText incidentTitleEntry = new EditText(context);
        incidentTitleEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int
                    count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                title = s.toString();
            }
        });
        templateViewLayout.addView(incidentTitle);
        templateViewLayout.addView(incidentTitleEntry);

        TextView incidentSummary = new TextView(context);
        incidentSummary.setTextSize(18f);
        incidentSummary.setTextColor(WHITE);
        incidentSummary.setText(R.string.incident_summary);
        EditText incidentSummaryEntry = new EditText(context);
        incidentSummaryEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int
                    count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                summary = s.toString();
            }
        });
        templateViewLayout.addView(incidentSummary);
        templateViewLayout.addView(incidentSummaryEntry);

        List<String> options = new ArrayList<>();
        options.add("-");

        Map<String, Marker> labelToMarker = new HashMap<>();

        PluginSpinner selectShapeSpinner = generateSpinner(context);
        ArrayAdapter<String> adp = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item,
                options
        );
        selectShapeSpinner.setAdapter(adp);
        selectShapeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (selectShapeSpinner.getSelectedItem().toString().contentEquals("-")) {
                    latitude = null;
                    longitude = null;
                    return;
                }

                Marker marker = labelToMarker.get(selectShapeSpinner.getSelectedItem().toString());
                if (marker != null) {
                    latitude = Double.toString(marker.getPoint().getLatitude());
                    longitude = Double.toString(marker.getPoint().getLongitude());
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        Button button = new Button(context);
        button.setText(R.string.incident_specify_location);
        button.setLayoutParams(layoutParamsButton);
        //TextPrompt container = TextPrompt.getInstance();
        button.setOnClickListener(view -> {
            int color = Constants.UNSELECTED_SHAPE_STROKE_COLOR;
            MapGroup drawingGroup = mapView.getRootGroup().findMapGroup(
                    "Drawing Objects");
            MiscUtils.toast("Tap on the incident location.");
            ShapeCreator creator = new PointCreatorCustom(mapView, drawingGroup,
                    color,
                    "Point",
                    Constants.SHAPE_TYPE.WATCH.toString(),
                    new ShapeCallback<DrawingShape>() {
                        @Override
                        public void onShapeCreated(DrawingShape shape) {
                        }

                        @Override
                        public void onShapeCreated(Marker marker) {
                            marker.setAlwaysShowText(true);

                            marker.setTitle(ShapeNameManager.getInstance().generatePointName(marker.getUID()));

                            marker.setTextColor(Constants.MAP_OBJECT_UNSELECTED_TEXT_COLOR);
                            int color = Constants.UNSELECTED_SHAPE_STROKE_COLOR;
                            marker.setColor(color);
                            Log.d(TAG, "marker uuid: " + marker.getUID());
                            MapStateListener.getInstance().addDrawnPoint(marker.getUID(), marker);
                            MapStateListener.getInstance().addSelectedPoint(marker);

                            latitude = Double.toString(marker.getPoint().getLatitude());
                            longitude = Double.toString(marker.getPoint().getLongitude());

                            labelToMarker.put(marker.getTitle(), marker);

                            //container.closePrompt();

                            if (!options.contains(marker.getTitle())) {
                                options.add(marker.getTitle());
                                selectShapeSpinner.setSelection(options.size() - 1);
                            }
                        }
                    });
            creator.begin();

            //container.displayPrompt("Tap on the location of the incident.");
        });

        templateViewLayout.addView(button);
        templateViewLayout.addView(selectShapeSpinner);

        Button submitButton = templateView.findViewById(R.id.submitButton);
        submitButton.setOnClickListener(view -> {

            // make sure that all required inputs are there before submitting
            if (latitude == null || latitude.isEmpty() ||
                longitude == null || longitude.isEmpty()) {
                Toast.makeText(MapView.getMapView().getContext(), "Please enter a location for the incident.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (title == null || title.isEmpty()) {
                Toast.makeText(MapView.getMapView().getContext(), "Please enter a title for the incident.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (summary == null || summary.isEmpty()) {
                Toast.makeText(MapView.getMapView().getContext(), "Please enter a summary for the incident.", Toast.LENGTH_SHORT).show();
                return;
            }

            CotEvent cotEvent = new CotEvent();
            cotEvent.setUID(MapView.getDeviceUid());
            cotEvent.setType("a-f-G-U-C");

            UUID incidentId = UUID.randomUUID();

            Map<String, String> metaData = new HashMap<>();
            metaData.put("latitude", latitude);
            metaData.put("longitude", longitude);
            metaData.put("callsign", MapView.getMapView().getDeviceCallsign());
            metaData.put("incident_id", incidentId.toString());
            metaData.put("title", title);
            metaData.put("summary", summary);

            Gson gson = new Gson();

            CotDetail detailElement = new CotDetail();
            CotDetail takCadElement = new CotDetail();
            takCadElement.setInnerText(gson.toJson(metaData));
            detailElement.addChild(takCadElement);

            cotEvent.setDetail(detailElement);

            CoordinatedTime coordinatedTime = new CoordinatedTime(
                    System.currentTimeMillis());
            cotEvent.setTime(coordinatedTime);
            cotEvent.setStart(coordinatedTime);
            cotEvent.setStale(coordinatedTime);

            cotEvent.setHow("tak-cad-incident");

            CotUtil.sendCotMessage(cotEvent);
        });

        setupTabs(context, templateView);

    }

    private PluginSpinner generateSpinner(Context context) {
        PluginSpinner pluginSpinner = new PluginSpinner(context);
        LinearLayout.LayoutParams layoutParamsText = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140);
        layoutParamsText.setMargins(50, 20, 50, 20);
        pluginSpinner.setPadding(0, 0, 0, 0);
        pluginSpinner.setLayoutParams(layoutParamsText);
        pluginSpinner.setBackgroundColor(Color.GRAY);
        return pluginSpinner;
    }

    @Override
    protected void disposeImpl() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_INCIDENT_CREATOR)) {

            selectTab(templateView, ownedTabIndex());

            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

        }
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
        return 0;
    }
}