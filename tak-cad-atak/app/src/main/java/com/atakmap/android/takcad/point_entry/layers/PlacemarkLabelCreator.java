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

package com.atakmap.android.takcad.point_entry.layers;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.atakmap.android.takcad.util.CotUtil;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Creates a CoT label on ATAK Map for a given KML Feature
 */
public class PlacemarkLabelCreator {
    private final static String TAG = PlacemarkLabelCreator.class.getCanonicalName();
    private static PlacemarkLabelCreator placemarkLabelCreator;
    private final ConcurrentMap<String, Set<String>> mapItemLabelsGenerated = new ConcurrentHashMap<>();

    public static PlacemarkLabelCreator getInstance(){
        if(placemarkLabelCreator == null){
            placemarkLabelCreator = new PlacemarkLabelCreator();
        }
        return placemarkLabelCreator;
    }

    private PlacemarkLabelCreator(){
    }

    /**
     * Create CoT Event
     *
     * I/E
     * <code>
     * <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
     * <event version="2.0" uid="908cd6be-0645-4fd0-a522-4c2ea796e72f" type="b-m-p-s-m" time="2021-08-16T15:56:38.827Z" start="2021-08-16T15:56:38.827Z" stale="2021-09-02T16:37:07.755Z" how="h-g-i-g-o">
     *   <point lat="42.4824248" lon="-71.6823338" hae="58.333" ce="9999999.0" le="42.5"/>
     *   <detail>
     *     <status readiness="true"/>
     *     <archive/>
     *     <archive/>
     *     <color argb="-16711936"/>
     *     <remarks/>
     *     <precisionlocation altsrc="DTED0"/>
     *     <usericon iconsetpath="COT_MAPPING_SPOTMAP/b-m-p-s-m/LABEL"/>
     *     <link uid="ANDROID-30552670594f6ab8" production_time="2021-08-16T15:48:34.185Z" type="a-f-G-U-C" parent_callsign="JEDI" relation="p-p"/>
     *     <contact callsign="G 1"/>
     *   </detail>
     * </event>
     * </code>
     *
     * @param feature Feature
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void createPlaceMarkLabel(String featSetName, Feature feature){
        String name = feature.getName();
        Set<String> features = mapItemLabelsGenerated.get(featSetName);
        if(features != null && features.contains(name)){
            return;
        }

        Envelope envelope = feature.getGeometry().getEnvelope();

        double centerX = (envelope.minX + envelope.maxX) / 2.0;
        double centerY = (envelope.minY + envelope.maxY) / 2.0;

        Log.d(TAG, "placemark with name " + name + " has center point: "
                + centerX + ", " + centerY);

        mapItemLabelsGenerated.computeIfAbsent(featSetName, k -> new HashSet<>()).add(name);
        CotEvent cotEvent = CotUtil.generateLabelCotEvent(name, centerX, centerY);

        Log.d(TAG, "generated CoTEvent: " + cotEvent);

        // Send locally
        CotMapComponent.getInternalDispatcher().dispatch(cotEvent);
    }

    public Set<String> getMapItemLabelsGenerated(String featSetName) {
        return mapItemLabelsGenerated.get(featSetName);
    }

}
