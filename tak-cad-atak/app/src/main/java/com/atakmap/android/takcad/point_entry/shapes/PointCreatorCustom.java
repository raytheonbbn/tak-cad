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

package com.atakmap.android.takcad.point_entry.shapes;

import static com.atakmap.android.takcad.Constants.SHAPE_TYPE_META_ATTR_NAME;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.takcad.point_entry.persistence.MapStateListener;
import com.atakmap.android.takcad.point_entry.TextPrompt;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PointCreatorCustom implements ShapeCreator {
    public static final Set<PointCreatorCustom> POINT_CREATORS = new HashSet<>();

    private static final String TAG = PointCreatorCustom.class.getSimpleName();
    private final String SHAPE_TYPE;

    private class PointListener implements View.OnTouchListener {
        private final MapView mapView;
        private final MapGroup drawingGroup;
        private final int pointColor;

        private PointListener(MapView mapView, MapGroup drawingGroup, int pointColor) {
            this.mapView = mapView;
            this.drawingGroup = drawingGroup;
            this.pointColor = pointColor;
        }

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            Log.d(TAG, "onTouch: " + motionEvent);

            if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                GeoPoint point = mapView.inverseWithElevation(
                        motionEvent.getX(), motionEvent.getY()).get();
                marker = new Marker(point, UUID.randomUUID().toString());
                marker.setTitle(name);
                marker.setType("b-m-p-w");
                marker.setMetaBoolean("drag", false);
                marker.setMetaBoolean("editable", true);
                marker.setMetaBoolean("addToObjList", false); // always hide these in overlays
                marker.setMetaString("how", "h-g-i-g-o"); // don't autostale it
                marker.setZOrder(Double.NEGATIVE_INFINITY);
                marker.setMetaInteger("color", pointColor);
                marker.setMetaString(SHAPE_TYPE_META_ATTR_NAME, SHAPE_TYPE);
                drawingGroup.addItem(marker);

                endShape();
                stopListening();
                shapeCallback.onShapeCreated(marker);
            }
            return false;
        }
    }

    private final MapView mapView;
    private final PointListener pointListener;
    private final String name;
    private Marker marker;
    private final ShapeCallback<DrawingShape> shapeCallback;

    public PointCreatorCustom(MapView mapView, MapGroup drawingGroup,
                              int pointColor, String name, String shapeType,
                              ShapeCallback<DrawingShape> callback) {
        this.mapView = mapView;
        this.name = name;
        this.pointListener = new PointListener(mapView, drawingGroup, pointColor);
        this.shapeCallback = callback;
        this.SHAPE_TYPE = shapeType;

        synchronized (POINT_CREATORS){
            POINT_CREATORS.add(this);
        }
    }

    /**
     * Puts ATAK in 'interactive mode', allowing the user to enter shape(s) on the map.
     */
    @Override
    public void begin() {
        // Save off the previous listener state, then clear all map event listeners.
        mapView.getMapEventDispatcher().pushListeners();
        mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_PRESS);
        mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_RELEASE);

        mapView.addOnTouchListener(pointListener);

        MapStateListener.getInstance().toggleDrawingMode(true);
    }

    /**
     * Cancel shape creation mode, deleting the points drawn on the screen.
     */
    @Override
    public void cancel() {
        mapView.removeOnTouchListener(pointListener);
        if (TextPrompt.getInstance() != null) {
            TextPrompt.getInstance().closePrompt();
        }

        MapStateListener.getInstance().toggleDrawingMode(false);
    }

    public void stopListening() {
        mapView.removeOnTouchListener(pointListener);
    }

    public void endShape() {
        mapView.removeOnTouchListener(pointListener);
        mapView.getMapEventDispatcher().clearListeners();
        mapView.getMapEventDispatcher().popListeners();
        mapView.getMapTouchController().skipDeconfliction(false);
        mapView.getMapTouchController().setToolActive(false);

        if (TextPrompt.getInstance() != null) {
            TextPrompt.getInstance().closePrompt();
        }

        MapStateListener.getInstance().toggleDrawingMode(false);
    }

}
