
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

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import com.atakmap.android.atakutils.MapItems;
import com.atakmap.android.takcad.TakCadMapComponent;
import com.atakmap.android.takcad.util.ActivityManager;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.bbn.atak.triggeraction.action.ActionExecutionException;
import com.bbn.atak.triggeraction.action.ActionExecutor;
import com.bbn.atak.triggeraction.action.ActionInitException;
import com.bbn.atak.triggeraction.action.ActionNotFoundException;
import com.bbn.atak.triggeraction.action.actions.PopupAction;
import com.bbn.atak.triggeraction.action.actions.SMSAction;
import com.bbn.atak.triggeraction.action.actions.VibrateAction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import transapps.maps.plugin.lifecycle.Lifecycle;

public class TakCadLifecycle implements Lifecycle {
    private static final String POPUP_ACTION = "popupAction";
    private final Context pluginContext;
    private final Collection<MapComponent> overlays;
    private MapView mapView;
    private static Activity atakActivity;
    private final static String TAG = "DinoLifecycle";
    private ActionExecutor executor;
    private boolean isAtakClosing = false;
    public TakCadLifecycle(Context ctx) {
        this.pluginContext = ctx;
        this.overlays = new LinkedList<>();
        this.mapView = null;
        PluginNativeLoader.init(ctx);
    }

    @Override
    public void onConfigurationChanged(Configuration arg0) {
        for (MapComponent c : this.overlays)
            c.onConfigurationChanged(arg0);
    }

    @Override
    public void onCreate(final Activity arg0,
            final transapps.mapi.MapView arg1) {
        this.atakActivity = arg0;
        ActivityManager.getInstance().setActivity(atakActivity);
        if (arg1 == null || !(arg1.getView() instanceof MapView)) {
            Log.w(TAG, "This plugin is only compatible with ATAK MapView");
            return;
        }
        this.mapView = (MapView) arg1.getView();
        TakCadLifecycle.this.overlays
                .add(new TakCadMapComponent());

        // create components
        Iterator<MapComponent> iter = TakCadLifecycle.this.overlays
                .iterator();
        MapComponent c;
        while (iter.hasNext()) {
            c = iter.next();
            try {
                c.onCreate(TakCadLifecycle.this.pluginContext,
                        arg0.getIntent(),
                        TakCadLifecycle.this.mapView);
            } catch (Exception e) {
                Log.w(TAG,
                        "Unhandled exception trying to create overlays MapComponent",
                        e);
                iter.remove();
            }
        }




        Log.d(TAG, "Adding event listeners for remove aircraft");


        MapView.getMapView().getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_REMOVED, mapEvent -> {
            if(mapEvent.getItem().getType().equals("u-d-v-m") && !isAtakClosing){

                String uid = mapEvent.getItem().getUID();
                Log.d(TAG, "You removed an aircraft: " + uid);

                MapItems.deleteMapItemOnOtherDevicesViaCoT(uid);

                //also delete the geofence

                String geofenceUid = uid + "-geoFence";
                MapGroup shapeGroup = MapItems.getShapeGroup(mapView);
                MapItem geofence = MapItems.findMatchingMapItem(i -> i.getUID().equals(geofenceUid), shapeGroup, false);
                if (geofence!=null){
                    shapeGroup.removeItem(geofence);
                    MapItems.deleteMapItemOnOtherDevicesViaCoT(geofenceUid);
                }


            }
        });

        CommsMapComponent.getInstance().addOnCotEventListener(new CotServiceRemote.CotEventListener() {
            @Override
            public void onCotEvent(CotEvent cotEvent, Bundle bundle) {
                android.util.Log.d(TAG, "receiveCotMessage: " + cotEvent + " from server IP: " + bundle.getString("serverFrom"));
                switch(cotEvent.getType()) {
                    case "trigger.action.notification":
                        try {
                            CotDetail detail = cotEvent.findDetail("execargs");
                            //<entry><key>callsigns</key><value>[GRAND SLAM CTL]</value></entry>
                            List<CotDetail> args = detail.getChildren();
                            Map<String, Object> execParams = new HashMap<>();
                            for (CotDetail entry : args) {
                                String key = entry.getChild("key").getInnerText();
                                String val = entry.getChild("value").getInnerText();
                                execParams.put(key, val);
                                Log.d(TAG, "TEST---> key: " + key + " value: " + val);
                            }



                            //executor.executeActionByName("speechAction", execParams);
                            executor.executeActionByName(POPUP_ACTION, execParams);

                        } catch (ActionNotFoundException | ActionExecutionException e) {
                            Log.e(TAG,"Error running action", e);
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        executor = new ActionExecutor(pluginContext, TakCadLifecycle.getCurrentActivity());
        Map<String, Object> speechConfigParams = new HashMap<>();
        speechConfigParams.put("soe_template", "The following tasks are past due: $TASKS");
        speechConfigParams.put("geofence_template", "$CALLSIGNS violated a geofence");
        speechConfigParams.put("cargo_arrived_template", "$CALLSIGNS is at main hanger 1");
        try {
            executor.registerAction("vibrateAction", new VibrateAction(), null);
            // ** TODO The line below causes ATAK to crash on uninstall!
            //executor.registerAction("speechAction", new SpeechSynthesizerAction(), speechConfigParams);
            executor.registerAction("smsAction", new SMSAction(), null);
            executor.registerAction(POPUP_ACTION, new PopupAction(), null);
        } catch (ActionInitException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void onDestroy() {
        Log.d(TAG, "ON DESTROY");
        isAtakClosing = true;
        for (MapComponent c : this.overlays)
            c.onDestroy(this.pluginContext, this.mapView);
    }

    @Override
    public void onFinish() {
        // XXX - no corresponding MapComponent method
        Log.d(TAG, "ON FINISH");
    }

    @Override
    public void onPause() {
        Log.d(TAG, "ON PAUSE");
        for (MapComponent c : this.overlays)
            c.onPause(this.pluginContext, this.mapView);
    }

    @Override
    public void onResume() {
        for (MapComponent c : this.overlays)
            c.onResume(this.pluginContext, this.mapView);
    }

    @Override
    public void onStart() {
        for (MapComponent c : this.overlays)
            c.onStart(this.pluginContext, this.mapView);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "ON STOP");
        for (MapComponent c : this.overlays)
            c.onStop(this.pluginContext, this.mapView);
    }

    public static Activity getCurrentActivity() {
        return atakActivity;
    }
}
