
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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.takcad.plugin.R;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.android.takcad.receivers.ReportIncidentDropDownReceiver;
import com.atakmap.android.takcad.receivers.ActiveIncidentsDropDownReceiver;
import com.atakmap.android.takcad.receivers.SettingsDropDownReceiver;
import com.atakmap.android.takcad.routing.DirectionsResponsePojos;
import com.atakmap.android.takcad.routing.OpenRouteApiManager;
import com.atakmap.android.takcad.routing.OpenRouteDirectionResponse;
import com.atakmap.android.takcad.routing.OpenRouteFunctions;
import com.atakmap.android.takcad.util.CotUtil;
import com.atakmap.android.takcad.util.MiscUtils;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class TakCadMapComponent extends DropDownMapComponent {

    private static final String TAG = TakCadMapComponent.class.getSimpleName();

    public static Context pluginContext;

    private ReportIncidentDropDownReceiver icDdr = null;
    private ActiveIncidentsDropDownReceiver ivDdr = null;
    private SettingsDropDownReceiver sDdr = null;

    private Handler handler;

    @SuppressLint("MissingPermission")
    public void onCreate(final Context context, Intent intent,
                         final MapView view) {

        Log.d(TAG, "Creating the tak cad plugin.");

        OpenRouteApiManager.instantiate();

        String openRouteApiKey = OpenRouteApiManager.getInstance().getOpenRouteApiKey();
        if (openRouteApiKey == null || openRouteApiKey.isEmpty()) {
            MiscUtils.toast("TAK CAD: Please Set Your Open Route Service API Key in Settings!");
        }

        IncidentResponderManager.getInstance().start();

        handler = new Handler(Looper.getMainLooper());

        // NOTE: R.style.ATAKPluginTheme does not support TabLayout, so
        // I needed to change the theme to AppCompat.
        context.setTheme(R.style.Theme_AppCompat);
        super.onCreate(context, intent, view);
        pluginContext = context;

        icDdr = new ReportIncidentDropDownReceiver(view, context);

        DocumentedIntentFilter ddFilterIc = new DocumentedIntentFilter();
        ddFilterIc.addAction(ReportIncidentDropDownReceiver.SHOW_INCIDENT_CREATOR);
        registerDropDownReceiver(icDdr, ddFilterIc);

        ivDdr = new ActiveIncidentsDropDownReceiver(view, context);

        DocumentedIntentFilter ddFilterIv = new DocumentedIntentFilter();
        ddFilterIv.addAction(ActiveIncidentsDropDownReceiver.SHOW_INCIDENT_VIEWER);
        registerDropDownReceiver(ivDdr, ddFilterIv);

        sDdr = new SettingsDropDownReceiver(view, context);

        DocumentedIntentFilter ddFilterS = new DocumentedIntentFilter();
        ddFilterS.addAction(SettingsDropDownReceiver.SHOW_PLUGIN);
        registerDropDownReceiver(sDdr, ddFilterS);

        CotUtil.setCotEventListener(event -> {

            Log.d(TAG, "onCotEvent (util): " + event);

            if (event.getHow().equals("tak-cad-incident")) {

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Got TAK CAD incident.");

                        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                        Map<String, String> incidentMetaData = new Gson().fromJson(MiscUtils.parseXml(event.getDetail().toString()), mapType);
                        String incidentId = incidentMetaData.get("incident_id");

                        String latitude = incidentMetaData.get("latitude");
                        String longitude = incidentMetaData.get("longitude");
                        String title = incidentMetaData.get("title");
                        String summary = incidentMetaData.get("summary");

                        Log.d(TAG, "Got coordinates of incident: " + longitude + "," + latitude);

                        String myLatitude = Double.toString(MapView.getMapView().getSelfMarker().getPoint().getLatitude());
                        String myLongitude = Double.toString(MapView.getMapView().getSelfMarker().getPoint().getLongitude());

                        Log.d(TAG, "Got my coordinates: " + myLongitude + "," + myLatitude);

                        String openRouteApiKey = OpenRouteApiManager.getInstance().getOpenRouteApiKey();
                        if (openRouteApiKey == null || openRouteApiKey.isEmpty()) {
                            MiscUtils.toast("TAK CAD: Please Set Your Open Route Service API Key in Settings!");
                            return;
                        }

                        OpenRouteFunctions.getDirections(myLatitude, myLongitude, latitude, longitude, openRouteApiKey,
                                new OpenRouteDirectionResponse() {
                                    @Override
                                    public void processDirections(DirectionsResponsePojos.Root response) {
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {

                                                if (response != null && response.features != null && response.features.get(0) != null) {

                                                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MapView.getMapView().getContext());
                                                    dialogBuilder.setTitle("New Incident: Do you want to respond?");

                                                    DirectionsResponsePojos.Feature feature = response.features.get(0);
                                                    double durationInMinutes =
                                                            MiscUtils.convertSecondsToMinutes(feature.properties.summary.duration);
                                                    String minutesTimeString = MiscUtils.convertMinutesToTimeString(durationInMinutes);

                                                    dialogBuilder.setMessage(
                                                            "Incident Title: " + title + "\n" +
                                                                    "Incident Summary: " + summary + "\n" +
                                                                    "ETA: " + minutesTimeString
                                                    );
                                                    dialogBuilder.setPositiveButton("Yes", (DialogInterface dialog, int which) -> {
                                                        Log.d(TAG, "Responding to incident...");

                                                        CotEvent cotEvent = new CotEvent();
                                                        cotEvent.setUID(MapView.getDeviceUid());
                                                        cotEvent.setType("a-f-G-U-C");

                                                        Map<String, String> responseMetaData = new HashMap<>();

                                                        Gson gson = new Gson();

                                                        responseMetaData.put("callsign", MapView.getMapView().getDeviceCallsign());
                                                        responseMetaData.put("incident_id", incidentId);
                                                        responseMetaData.put("title", title);
                                                        responseMetaData.put("summary", summary);
                                                        responseMetaData.put("latitude", latitude);
                                                        responseMetaData.put("longitude", longitude);
                                                        responseMetaData.put("version", "0");
                                                        try {
                                                            responseMetaData.put("directions", new ObjectMapper().writeValueAsString(response));
                                                        } catch (JsonProcessingException e) {
                                                            Log.e(TAG, "Error with getting directions json string", e);
                                                        }
                                                        CotDetail detailElement = new CotDetail();
                                                        CotDetail takCadElement = new CotDetail();
                                                        takCadElement.setInnerText(gson.toJson(responseMetaData));
                                                        detailElement.addChild(takCadElement);

                                                        cotEvent.setDetail(detailElement);

                                                        CoordinatedTime coordinatedTime = new CoordinatedTime(
                                                                System.currentTimeMillis());
                                                        cotEvent.setTime(coordinatedTime);
                                                        cotEvent.setStart(coordinatedTime);
                                                        cotEvent.setStale(coordinatedTime);

                                                        cotEvent.setHow("tak-cad-response");

                                                        Log.d(TAG, "Generated tak cad response: " + cotEvent);

                                                        CotUtil.sendCotMessage(cotEvent);

                                                        IncidentResponderManager.IncidentInfo incidentInfo = new IncidentResponderManager.IncidentInfo();
                                                        incidentInfo.latitude = latitude;
                                                        incidentInfo.longitude = longitude;
                                                        incidentInfo.summary = summary;
                                                        incidentInfo.title = title;

                                                        IncidentResponderManager.getInstance().addIncidentInfo(incidentId, incidentInfo);
                                                        IncidentResponderManager.getInstance().addMyResponseVersion(incidentId, 0);

                                                        dialog.dismiss();

                                                    });
                                                    dialogBuilder.setNegativeButton("No", (DialogInterface dialog, int which) -> {
                                                        Log.d(TAG, "Ignoring incident.");
                                                        dialog.dismiss();
                                                    });
                                                    dialogBuilder.setCancelable(false);
                                                    AlertDialog dialog = dialogBuilder.create();
                                                    dialog.setCanceledOnTouchOutside(false);
                                                    dialog.show();

                                                }
                                            }
                                        });
                                    }
                                });
                    }
                });

            } else if (event.getHow().equals("tak-cad-response")) {

                Log.d(TAG, "Got TAK CAD response.");

                Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> responseMetaData = new Gson().fromJson(MiscUtils.parseXml(event.getDetail().toString()), mapType);

                String callSign = responseMetaData.get("callsign");
                String incidentId = responseMetaData.get("incident_id");
                String directionsJson = responseMetaData.get("directions");
                String title = responseMetaData.get("title");
                String summary = responseMetaData.get("summary");
                String latitude = responseMetaData.get("latitude");
                String longitude = responseMetaData.get("longitude");
                String version = responseMetaData.get("version");

                DirectionsResponsePojos.Root directions = null;

                try {
                    directions = new ObjectMapper().readValue(directionsJson, DirectionsResponsePojos.Root.class);
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Failed to parse directions pojo", e);
                }

                IncidentResponderManager.ResponderInfo responderInfo = new IncidentResponderManager.ResponderInfo();
                responderInfo.callSign = callSign;
                responderInfo.directions = directions;
                responderInfo.version = Integer.parseInt(version != null ? version : "-1");

                IncidentResponderManager.IncidentInfo incidentInfo = new IncidentResponderManager.IncidentInfo();
                incidentInfo.latitude = latitude;
                incidentInfo.longitude = longitude;
                incidentInfo.title = title;
                incidentInfo.summary = summary;

                if (responderInfo.directions != null && responderInfo.directions.features != null && responderInfo.directions.features.get(0) != null) {
                    IncidentResponderManager.getInstance().addIncidentInfo(incidentId, incidentInfo);
                    IncidentResponderManager.getInstance().addResponderInfo(incidentId, responderInfo);
                }

                Log.d(TAG, "Current incident id to responder info map: " +
                        IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo());

            } else if (event.getHow().equals("tak-cad-response-update")) {

                Log.d(TAG, "Got TAK CAD response update.");

                Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> responseMetaData = new Gson().fromJson(MiscUtils.parseXml(event.getDetail().toString()), mapType);

                String callSign = responseMetaData.get("callsign");
                String incidentId = responseMetaData.get("incident_id");
                String directionsJson = responseMetaData.get("directions");
                String version = responseMetaData.get("version");

                DirectionsResponsePojos.Root directions = null;

                try {
                    directions = new ObjectMapper().readValue(directionsJson, DirectionsResponsePojos.Root.class);
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Failed to parse directions pojo", e);
                }

                IncidentResponderManager.ResponderInfo responderInfo = new IncidentResponderManager.ResponderInfo();
                responderInfo.callSign = callSign;
                responderInfo.version = Integer.parseInt(version != null ? version : "-1");
                responderInfo.directions = directions;

                if (responderInfo.directions != null && responderInfo.directions.features != null && responderInfo.directions.features.get(0) != null) {
                    if (IncidentResponderManager.getInstance().getIncidentInfo(incidentId) != null) {
                        IncidentResponderManager.getInstance().addResponderInfo(incidentId, responderInfo);
                    }
                }
            }
        });

    }

}
