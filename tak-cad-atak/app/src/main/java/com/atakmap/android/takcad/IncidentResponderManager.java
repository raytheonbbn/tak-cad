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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.routing.DirectionsResponsePojos;
import com.atakmap.android.takcad.routing.OpenRouteApiManager;
import com.atakmap.android.takcad.routing.OpenRouteDirectionResponse;
import com.atakmap.android.takcad.routing.OpenRouteFunctions;
import com.atakmap.android.takcad.util.CotUtil;
import com.atakmap.android.takcad.util.MiscUtils;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class IncidentResponderManager extends Thread {

    private static final String TAG = IncidentResponderManager.class.getSimpleName();

    public static IncidentResponderManager INSTANCE = null;

    private final int TIME_UPDATE_INTERVAL = 1000;
    private final int ROUTE_UPDATE_INTERVAL = 10000;

    private final int MSG_UPDATE_MAP_ITEMS = 0;
    private final int MSG_UPDATE_ROUTES = 1;

    private Handler handler;

    public static class IncidentInfo {
        public String title;
        public String summary;
        public String latitude;
        public String longitude;
    }

    public static class ResponderInfo {

        public String callSign;
        public DirectionsResponsePojos.Root directions;
        public int version;

        public ResponderInfo() {

        }

        @Override
        public String toString() {
            return "ResponderInfo{" +
                    "callSign='" + callSign + '\'' +
                    ", directions=" + directions +
                    ", version=" + version +
                    '}';
        }
    }

    public static class DrawnRoute {

        public MapItem mapItem;
        public int version;

    }

    private Map<String, List<ResponderInfo>> myIncidentIdToResponderInfo = new HashMap<>();
    private Map<String, IncidentInfo> incidentIdToIncidentInfo = new HashMap<>();
    private Map<String, Integer> otherIncidentIdToLastResponseVersion = new HashMap<>();
    private Map<String, Map<String, DrawnRoute>> drawnRoutes = new HashMap<>();
    private String lastSelectedIncidentId = null;

    private IncidentResponderManager() {

        handler = new Handler(Looper.myLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {

                    case MSG_UPDATE_MAP_ITEMS: {

                        Log.v(TAG, "Got message to update map items.");

                        if (lastSelectedIncidentId != null) {
                            MapGroup drawingGroup = MapView.getMapView().getRootGroup().findMapGroup(
                                    "Drawing Objects");

                            boolean foundIncidentId = false;
                            Iterator<Map.Entry<String, Map<String, DrawnRoute>>> it = drawnRoutes.entrySet().iterator();
                            while (it.hasNext()) {

                                Map.Entry<String, Map<String, DrawnRoute>> entry = it.next();
                                String drawnIncidentId = entry.getKey();

                                if (!drawnIncidentId.equals(lastSelectedIncidentId)) {

                                    // if there are routes drawn for a previously selected incident id, remove them

                                    Map<String, DrawnRoute> drawnRoutesForIncident = drawnRoutes.get(drawnIncidentId);

                                    if (drawnRoutesForIncident != null) {
                                        for (DrawnRoute drawnRoute : drawnRoutesForIncident.values()) {
                                            drawingGroup.removeItem(drawnRoute.mapItem);
                                        }
                                    } else {
                                        Log.w(TAG, "drawnRoutesForIncident was null for id " + drawnIncidentId);
                                    }

                                    it.remove();

                                } else {

                                    // draw and or update any of the routes that are being drawn for the
                                    // last selected incident id

                                    List<ResponderInfo> responderInfoList = myIncidentIdToResponderInfo.get(lastSelectedIncidentId);

                                    Map<String, ResponderInfo> currentResponderInfos = new HashMap<>();
                                    for (ResponderInfo responderInfo : responderInfoList) {
                                        currentResponderInfos.put(responderInfo.callSign, responderInfo);
                                    }

                                    for (String callSign : entry.getValue().keySet()) {
                                        DrawnRoute drawnRoute = entry.getValue().get(callSign);
                                        if (drawnRoute != null) {
                                            ResponderInfo responderInfo = currentResponderInfos.get(callSign);
                                            if (responderInfo != null) {
                                                Integer currentRouteVersion = responderInfo.version;
                                                if (drawnRoute.version < currentRouteVersion) {
                                                    drawingGroup.removeItem(drawnRoute.mapItem);
                                                    DrawingShape routeShape = MiscUtils.convertDirectionsPojoToDrawingShape(responderInfo.directions, drawingGroup);
                                                    if (routeShape != null) {
                                                        drawnRoute.mapItem = routeShape;
                                                        drawingGroup.addItem(drawnRoute.mapItem);
                                                        drawnRoute.version = currentRouteVersion;
                                                    }
                                                }
                                            } else {
                                                Log.w(TAG, "responderInfo was null.");
                                            }
                                        } else {
                                            Log.w(TAG, "drawnRoute was null.");
                                        }

                                    }

                                    foundIncidentId = true;

                                }
                            }

                            // if we didn't find the incident id, then drawn it and add it
                            if (!foundIncidentId) {
                                Map<String, DrawnRoute> routesForIncidentId = new HashMap<>();

                                List<ResponderInfo> responderInfoList = myIncidentIdToResponderInfo.get(lastSelectedIncidentId);

                                if (responderInfoList != null) {
                                    for (ResponderInfo responderInfo : responderInfoList) {
                                        DrawnRoute newRoute = new DrawnRoute();
                                        newRoute.version = responderInfo.version;
                                        newRoute.mapItem = MiscUtils.convertDirectionsPojoToDrawingShape(responderInfo.directions, drawingGroup);
                                        drawingGroup.addItem(newRoute.mapItem);
                                        routesForIncidentId.put(responderInfo.callSign, newRoute);
                                    }
                                } else {
                                    Log.w(TAG, "responderInfoList null.");
                                }

                                drawnRoutes.put(lastSelectedIncidentId, routesForIncidentId);
                            }

                        }

                        Message updateTimeMsg = new Message();
                        updateTimeMsg.what = MSG_UPDATE_MAP_ITEMS;
                        handler.sendMessageDelayed(updateTimeMsg, TIME_UPDATE_INTERVAL);

                        break;
                    }
                    case MSG_UPDATE_ROUTES: {

                        Log.d(TAG, "Current otherIncidentIdToLastResponseVersion: " + otherIncidentIdToLastResponseVersion);

                        // do processing for any incidents we have responded to that we need to send
                        // updates for
                        for (String otherIncidentId : otherIncidentIdToLastResponseVersion.keySet()) {

                            Integer lastVersion = otherIncidentIdToLastResponseVersion.get(otherIncidentId);
                            IncidentInfo incidentInfo = incidentIdToIncidentInfo.get(otherIncidentId);

                            if (lastVersion != null && incidentInfo != null) {

                                String myLatitude = Double.toString(MapView.getMapView().getSelfMarker().getPoint().getLatitude());
                                String myLongitude = Double.toString(MapView.getMapView().getSelfMarker().getPoint().getLongitude());

                                com.atakmap.coremap.log.Log.d(TAG, "Got my coordinates: " + myLongitude + "," + myLatitude);

                                String openRouteApiKey = OpenRouteApiManager.getInstance().getOpenRouteApiKey();
                                if (openRouteApiKey == null || openRouteApiKey.isEmpty()) {
                                    MiscUtils.toast("TAK CAD: Please Set Your Open Route Service API Key in Settings!");
                                    return false;
                                }

                                OpenRouteFunctions.getDirections(myLatitude, myLongitude, incidentInfo.latitude, incidentInfo.longitude, openRouteApiKey,
                                        new OpenRouteDirectionResponse() {
                                            @Override
                                            public void processDirections(DirectionsResponsePojos.Root response) {
                                                handler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        CotEvent cotEvent = new CotEvent();
                                                        cotEvent.setUID(MapView.getDeviceUid());
                                                        cotEvent.setType("a-f-G-U-C");

                                                        Map<String, String> responseMetaData = new HashMap<>();

                                                        Gson gson = new Gson();

                                                        responseMetaData.put("callsign", MapView.getMapView().getDeviceCallsign());
                                                        responseMetaData.put("incident_id", otherIncidentId);
                                                        responseMetaData.put("version", Integer.toString(lastVersion + 1));
                                                        try {
                                                            responseMetaData.put("directions", new ObjectMapper().writeValueAsString(response));
                                                        } catch (JsonProcessingException e) {
                                                            com.atakmap.coremap.log.Log.e(TAG, "Error with getting directions json string", e);
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

                                                        cotEvent.setHow("tak-cad-response-update");

                                                        com.atakmap.coremap.log.Log.d(TAG, "Generated tak cad response: " + cotEvent);

                                                        CotUtil.sendCotMessage(cotEvent);

                                                        otherIncidentIdToLastResponseVersion.put(otherIncidentId, lastVersion + 1);
                                                    }
                                                });
                                            }
                                        });

                            } else {
                                Log.w(TAG, "lastVersion or incidentInfo was null");
                            }
                        }

                        Message updateTimeMsg = new Message();
                        updateTimeMsg.what = MSG_UPDATE_ROUTES;
                        handler.sendMessageDelayed(updateTimeMsg, ROUTE_UPDATE_INTERVAL);

                        break;
                    }
                }

                return true;
            }
        });

        Message updateTimeMsg = new Message();
        updateTimeMsg.what = MSG_UPDATE_MAP_ITEMS;
        handler.sendMessageDelayed(updateTimeMsg, TIME_UPDATE_INTERVAL);

        Message updateRouteMsg = new Message();
        updateRouteMsg.what = MSG_UPDATE_ROUTES;
        handler.sendMessageDelayed(updateRouteMsg, ROUTE_UPDATE_INTERVAL);

    }

    public static IncidentResponderManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IncidentResponderManager();
        }
        return INSTANCE;
    }

    public void addResponderInfo(String incidentId, ResponderInfo responderInfo) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                myIncidentIdToResponderInfo.computeIfAbsent(incidentId, k -> new ArrayList<>());
                List<ResponderInfo> responderInfoList = myIncidentIdToResponderInfo.get(incidentId);
                boolean foundResponderInfo = false;
                for (ResponderInfo existingResponderInfo : responderInfoList) {
                    if (existingResponderInfo.callSign.equals(responderInfo.callSign) &&
                        existingResponderInfo.version < responderInfo.version) {
                        existingResponderInfo.directions = responderInfo.directions;
                        existingResponderInfo.version = responderInfo.version;
                        foundResponderInfo = true;
                        break;
                    }
                }
                if (!foundResponderInfo) {
                    myIncidentIdToResponderInfo.get(incidentId).add(responderInfo);
                }
            }
        });
    }

    public void addMyResponseVersion(String incidentId, int version) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                otherIncidentIdToLastResponseVersion.put(incidentId, version);
            }
        });
    }

    public Map<String, List<ResponderInfo>> getMyIncidentIdToResponderInfo() {
        return new HashMap<>(myIncidentIdToResponderInfo);
    }

    public void addIncidentInfo(String incidentId, IncidentInfo incidentInfo) {
        incidentIdToIncidentInfo.put(incidentId, incidentInfo);
    }

    public IncidentInfo getIncidentInfo(String incidentId) {
        return incidentIdToIncidentInfo.get(incidentId);
    }

    public void updateLastSelectedIncidentId(String incidentId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                lastSelectedIncidentId = incidentId;
            }
        });
    }

}
