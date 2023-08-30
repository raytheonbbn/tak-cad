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

package com.atakmap.android.takcad.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.importexport.AbstractCotEventMarshal;
import com.atakmap.android.importexport.MarshalManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.statesaver.StateSaverPublisher;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class CotUtil {
    private static final String TAG = CotUtil.class.getSimpleName();
    private static AbstractCotEventMarshal acem;

    public static void sendCotMessage(CotEvent cotEvent){
        CotMapComponent.getExternalDispatcher().dispatch(cotEvent);
    }

    public interface CotEventListener{
        void onReceiveCotEvent(CotEvent cotEvent);
    }

    public static void setCotEventListener(final CotEventListener cotEventListener){
        AtakBroadcast.DocumentedIntentFilter intentFilter = new AtakBroadcast.DocumentedIntentFilter(
                "com.atakmap.android.statesaver.statesaver_complete_load");

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "received action: " + intent.getAction());
                initialize(cotEventListener, null);
            }
        };

        if (StateSaverPublisher.isFinished()) {
            initialize(cotEventListener, null);
        } else {
            AtakBroadcast.getInstance().registerReceiver(br, intentFilter);
        }
    }

    public static void setCotEventListener(final CotEventListener cotEventListener, final String cotEventType){
        AtakBroadcast.DocumentedIntentFilter intentFilter = new AtakBroadcast.DocumentedIntentFilter(
                "com.atakmap.android.statesaver.statesaver_complete_load");

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "received action: " + intent.getAction());
                initialize(cotEventListener, cotEventType);
            }
        };

        if (StateSaverPublisher.isFinished()) {
            initialize(cotEventListener, null);
        } else {
            AtakBroadcast.getInstance().registerReceiver(br, intentFilter);
        }
    }

    private static void initialize(final CotEventListener cotEventListener, final String cotEventType){
        MarshalManager.registerMarshal(
                acem = new AbstractCotEventMarshal("CasPopup") {
                    @Override
                    protected boolean accept(final CotEvent event) {
                        if(cotEventType != null){
                            if (event.getType().equals(cotEventType)){
                                Log.d(TAG, "Accept: " + event.toString());
                                cotEventListener.onReceiveCotEvent(event);
                            }
                        }else {
                            Log.d(TAG, "Accept: " + event.toString());
                            cotEventListener.onReceiveCotEvent(event);
                        }
                        return false;
                    }

                    @Override
                    public int getPriorityLevel() {
                        return 2;
                    }
                });
    }

    public static CotEvent generateLabelCotEvent(String uid, double lon, double lat){
        CotEvent cotEvent = new CotEvent();
        cotEvent.setPoint(new CotPoint(lat, lon, 9999999.0, 9999999.0,
                9999999.0));

        cotEvent.setUID(uid);
        cotEvent.setVersion("2.0");
        cotEvent.setType("b-m-p-s-m");
        cotEvent.setHow("h-g-i-g-o");

        CoordinatedTime coordinatedTime = new CoordinatedTime(new Date().getTime());
        cotEvent.setTime(coordinatedTime);
        cotEvent.setStart(coordinatedTime);
        cotEvent.setStale(coordinatedTime.addDays(30));

        CotDetail outerCotDetail = new CotDetail();
        CotDetail userIcon = new CotDetail();
        userIcon.setElementName("usericon");
        userIcon.setAttribute("iconsetpath", "COT_MAPPING_SPOTMAP/b-m-p-s-m/LABEL");
        CotDetail callsign = new CotDetail();
        callsign.setElementName("contact");
        callsign.setAttribute("callsign", uid);

        CotDetail status = new CotDetail();
        status.setElementName("status");
        status.setAttribute("readiness", "true");
        CotDetail color = new CotDetail();
        color.setElementName("color");
        color.setAttribute("argb", "-16711936");
        CotDetail remarks = new CotDetail();
        remarks.setElementName("remarks");
        CotDetail precisionlocation = new CotDetail();
        precisionlocation.setElementName("precisionlocation");
        precisionlocation.setAttribute("altsrc", "DTED0");

        CotDetail link = new CotDetail();
        link.setElementName("link");
        link.setAttribute("uid", MapView.getDeviceUid());

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        link.setAttribute("production_time", format.format(new Date()));
        link.setAttribute("type", "a-f-G-U-C");
        link.setAttribute("parent_callsign", MapView.getMapView().getDeviceCallsign());
        link.setAttribute("relation", "p-p");

        outerCotDetail.addChild(userIcon);
        outerCotDetail.addChild(callsign);
        outerCotDetail.addChild(status);
        outerCotDetail.addChild(color);
        outerCotDetail.addChild(remarks);
        outerCotDetail.addChild(precisionlocation);
        outerCotDetail.addChild(link);

        cotEvent.setDetail(outerCotDetail);

        return cotEvent;
    }
}
