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

package com.atakmap.android.takcad.point_entry.persistence;

import android.util.Log;

import com.atakmap.android.takcad.point_entry.layers.PlacemarkLabelCreator;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.map.layer.feature.AttributeSet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CachedKMLPlacemarkInfo {
    private static CachedKMLPlacemarkInfo cachedKMLPlacemarkInfo;
    private final String TAG = CachedKMLPlacemarkInfo.class.getCanonicalName();

    private final Map<String, AttributeSet> placeMarkNameToAttributeSet = new ConcurrentHashMap<>();
    private final Set<String> buildingPlacemarks = new HashSet<>();
    private final Set<String> objectPlacemarks = new HashSet<>();
    private final Map<String, Set<String>> buildingToObjects = new ConcurrentHashMap<>();

    private String currentRegion = "";

    public static CachedKMLPlacemarkInfo getInstance(){
        if(cachedKMLPlacemarkInfo == null){
            cachedKMLPlacemarkInfo = new CachedKMLPlacemarkInfo();
        }
        return cachedKMLPlacemarkInfo;
    }

    private CachedKMLPlacemarkInfo(){
    }


    /**
     * Adapted from FeatureDataStore2DeepMapItemQuery
     *
     * @param attribs AttributeSet
     * @param name Placemark name
     * @param raw boolean raw data
     *
     * @return String
     */
    private String getAttributeAsString(AttributeSet attribs,
                                               String name, boolean raw) {
        Class<?> attribType = attribs.getAttributeType(name);
        if (attribType == null)
            return "";
        else if (attribType.equals(String.class))
            return attribs.getStringAttribute(name);
        else if (attribType.equals(Integer.TYPE)
                || attribType.equals(Integer.class))
            return String.valueOf(attribs.getIntAttribute(name));
        else if (attribType.equals(Double.TYPE)
                || attribType.equals(Double.class))
            return String.valueOf(attribs.getDoubleAttribute(name));
        else if (attribType.equals(Long.TYPE)
                || attribType.equals(Long.class)) {
            long val = attribs.getLongAttribute(name);
            if (raw)
                return String.valueOf(val);
            if ((name.toLowerCase(LocaleUtil.getCurrent()).contains("date") ||
                    name.toLowerCase(LocaleUtil.getCurrent()).contains("time"))
                    &&
                    val > 100000000000L) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy, HH:mm",
                        LocaleUtil.getCurrent());
                return sdf.format(new Date(val));
            } else
                return String.valueOf(val);
        } else
            return "";
    }

    public AttributeSet getPlacemarkAttributes(String placemarkName){
        return placeMarkNameToAttributeSet.get(placemarkName);
    }

    public String getAttributeAsString(AttributeSet attributeSet, String attribute){
        return getAttributeAsString(attributeSet, attribute, true);
    }

    public void recordPlacemark(String placemarkName, AttributeSet attributeSet){
        placeMarkNameToAttributeSet.put(placemarkName, attributeSet);

        String description = CachedKMLPlacemarkInfo.getInstance().getAttributeAsString(attributeSet,
                "description");
        if(description != null && description.contains("isEnterable=true")){
            addBuildingPlacemark(placemarkName);
        }else{
            addObjectPlacemark(placemarkName);
        }
    }

    public void addBuildingPlacemark(String buildingPlacemarkName){
        synchronized (buildingPlacemarks) {
            buildingPlacemarks.add(buildingPlacemarkName);
        }
    }

    public void addObjectPlacemark(String objectPlacemarkName){
        synchronized (objectPlacemarks) {
            objectPlacemarks.add(objectPlacemarkName);
        }
    }

    public boolean placemarkIsEnterable(String placemarkName){
        return buildingPlacemarks.contains(placemarkName);
    }

    public Set<String> getBuildingPlacemarks() {
        return buildingPlacemarks;
    }

    public Set<String> getObjectPlacemarks() {
        return objectPlacemarks;
    }

    /**
     * Gets current KML Region (i/e Shelby) name
     *
     * @return String kmlRegion id
     */
    public String getCurrentRegion() {
        return currentRegion;
    }

    public void setCurrentRegion(String currentRegion) {
        Log.d(TAG, "setting current region: " + currentRegion);
        this.currentRegion = currentRegion;
    }

    public boolean featureIsInCurrentRegion(String feature){
        return PlacemarkLabelCreator.getInstance().getMapItemLabelsGenerated(currentRegion)
                .contains(feature);
    }
}
