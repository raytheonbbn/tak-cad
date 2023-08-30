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

import com.atakmap.android.takcad.Constants;
import com.atakmap.android.takcad.point_entry.layers.PlacemarkLabelCreator;
import com.atakmap.android.takcad.util.MiscUtils;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores information about state of map, i/e agents selected, objects selected
 */
public class MapStateListener implements MapEventDispatcher.MapEventDispatchListener{
    private static MapStateListener mapStateListener;
    private final String TAG = MapStateListener.class.getCanonicalName();

    private final Set<String> allObjects = new HashSet<>();
    private final Set<String> selectedObjects = new HashSet<>();
    private final Set<String> selectedATs = new HashSet<>();
    private final Set<String> selectedTracks = new HashSet<>();

    private final Map<String, Shape> shapeUids = new HashMap<>();
    private final Set<Shape> selectedShapes = new HashSet<>();
    private final Map<String, String> markerUidToShapeUid = new HashMap<>();
    private final Map<String, Marker> shapeUidToMarker = new HashMap<>();

    private final Map<String, Marker> uidToPointDrawnMarker = new HashMap<>();
    private final Set<Marker> selectedMarkers = new HashSet<>();

    private boolean allBuildingAndObjectLabelsEnabled = true;
    private boolean buildingLabelsVisible = true, objectLabelsVisible = true;

    private long lastCurrentTimeMillis = System.currentTimeMillis();
    //private final Set<String> drawnShapes = new HashSet<>();
    private boolean isConstructingCommand = false;
    private boolean aprilTagsVisible = true;

    private final ConcurrentMap<String, Marker> placemarkLabelToMapItem =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Marker> trackUidToMarker =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Marker> uidToATMarker =
            new ConcurrentHashMap<>();

    private boolean drawing = false;
    private boolean allAgentsSelected = false;

    private String watchShapeSelected = null;
    private String gotoShapeSelected = null;
    private String unspecifiedShapeSelected = null;

    public GeoPoint[] getSelectedPoints(Constants.SHAPE_TYPE shape_type, boolean requirePolyShape) {
        return getSelectedPoints(shape_type, requirePolyShape, false);
    }

    /**
     * Gets selected shape point(s). Expects one shape to be selected for a given type
     * (Default/Unspecified, GoTo, Watch).
     *
     * @param shape_type required
     * @param requirePolyShape required
     * @return GeoPoint[]
     */
    public GeoPoint[] getSelectedPoints(Constants.SHAPE_TYPE shape_type, boolean requirePolyShape,
                                        boolean allowEmpty) {
        Shape selectedShape = null;
        Marker selectedMarker = null;
        int amountOfShapes = 0;

        for(Shape shape : selectedShapes){
            if((shape_type.equals(Constants.SHAPE_TYPE.UNSPECIFIED)
                    || shape.getMetaString(Constants.SHAPE_TYPE_META_ATTR_NAME, "")
                    .contentEquals(shape_type.toString())) && shape.getPoints().length > 1){
                if(requirePolyShape && shape.getPoints().length <= 1){
                    continue;
                }

                selectedShape = shape;
                amountOfShapes++;

                if(amountOfShapes > 1){
                    break;
                }
            }
        }

        if(!requirePolyShape) {
            for (Marker marker : selectedMarkers) {
                if ((shape_type.equals(Constants.SHAPE_TYPE.UNSPECIFIED)
                        || marker.getMetaString(Constants.SHAPE_TYPE_META_ATTR_NAME, "")
                        .contentEquals(shape_type.toString()))) {
                    selectedMarker = marker;
                    amountOfShapes++;

                    if (amountOfShapes > 1) {
                        break;
                    }
                }
            }
        }

        if(amountOfShapes > 1){
            MiscUtils.toast("Error, more than one shape is selected");
        }else if(amountOfShapes == 0 && !allowEmpty){
            if(selectedMarkers.isEmpty()) {
               // MiscUtils.toast("Please draw and select a shape area (plugin's drawing button).");
            }else{
                if(shape_type.equals(Constants.SHAPE_TYPE.UNSPECIFIED)){
                    MiscUtils.toast("Command requires selected shape to have multiple points.");
                }else{
                    MiscUtils.toast("Command requires selected shape to of type " + shape_type
                            + " and have multiple points.");
                }
            }
        }else{
            if(selectedShape != null){
                return selectedShape.getPoints();
            }else if(selectedMarker != null){
                GeoPoint[] geoPoints = new GeoPoint[1];
                geoPoints[0] = selectedMarker.getPoint();
                return geoPoints;
            }
        }

        return new GeoPoint[0];
    }

    public void addShapeListener(ShapeListener shapeSelectedListener) {
        shapeListeners.add(shapeSelectedListener);
    }

    public void removeShapeListener(ShapeListener shapeSelectedListener) {
        shapeListeners.remove(shapeSelectedListener);
    }

    public void cacheReferenceToTrackMapItem(Marker marker) {
        // ignore off screen tags
        marker.setMetaBoolean("ignoreOffscreen", true);
        trackUidToMarker.put(marker.getUID(), marker);
    }

    public Set<String> getAllTracks() {
        return trackUidToMarker.keySet();
    }


    public interface EntityListUpdatedListener{
        void updatedObjectsList(Set<String> selectedObjects);
        void updateATList(Set<String> selectedATs);
        void updateTrackList(Set<String> selectedTracks);
    }

    private final Set<EntityListUpdatedListener> entityListUpdatedListeners = new HashSet<>();

    public interface ShapeListener{
        void shapeListUpdated();
    }

    private final Set<ShapeListener> shapeListeners = new HashSet<>();

    public static MapStateListener getInstance(){
        if(mapStateListener == null){
            mapStateListener = new MapStateListener();
        }
        return mapStateListener;
    }

    public static void invoke(){
        getInstance();
    }

    private MapStateListener(){
        MapView.getMapView().getMapEventDispatcher()
                .addMapEventListener(MapEvent.ITEM_ADDED,this);


        MapView.getMapView().getMapEventDispatcher()
           .addMapEventListener(MapEvent.MAP_SCALE,this);
        MapView.getMapView().getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_SCROLL,this);
    }

    public Set<String> getAllObjects() {
        return allObjects;
    }

    public Set<String> getSelectedObjects() {
        return selectedObjects;
    }

    public Set<String> getSelectedATs() {
        return selectedATs;
    }

    public Set<String> getSelectedTracks() {
        return selectedTracks;
    }

    public void clearSelections(){
        /*for(Agent agent : new HashSet<>(getSelectedAgents())){
            removeSelectedAgent(agent);
        }
        for (String s : new HashSet<>(getSelectedObjects())) {
            removeSelectedObject(s);
        }
        for (Marker m : new HashSet<>(getSelectedPoints())){
            removeSelectedPoint(m);
        }
        for (Shape s : new HashSet<>(getSelectedShapes())){
            removeSelectedShape(s);
        }
        for (String t : new HashSet<>(getSelectedTracks())){
            removeSelectedTrack(t);
        }

        MapStateListener.getInstance().toggleLabelsBasedOnZoom();

        gotoShapeSelected = null;
        watchShapeSelected = null;
        unspecifiedShapeSelected = null;*/
    }


    @Override
    public void onMapEvent(MapEvent mapEvent) {
        if(mapEvent.getType().contentEquals(MapEvent.MAP_SCALE)
        || mapEvent.getType().contentEquals(MapEvent.MAP_SCROLL)){
            // only update labels every 200 msec
            if(System.currentTimeMillis() - lastCurrentTimeMillis < 200
                    || MapStateListener.getInstance().isConstructingCommand()){
                return;
            }
            lastCurrentTimeMillis = System.currentTimeMillis();

            toggleLabelsBasedOnZoom();
        } else {
            Log.d(TAG, "onMapEvent: " + mapEvent.getItem().getTitle() + " "
                   + mapEvent.getItem().getType() + " " + mapEvent.getItem().getClass());

            // ignore off screen tags
            mapEvent.getItem().setMetaBoolean("ignoreOffscreen", true);

                if(mapEvent.getItem() instanceof Rectangle){
                    Rectangle rectangle = (Rectangle) mapEvent.getItem();
                    addDrawnShape(mapEvent.getItem().getUID(), rectangle);
                    addDrawnShapeMarker(rectangle.getCenterMarker(), mapEvent.getItem().getUID());
                    ShapeNameManager.getInstance().addPolygonName(mapEvent.getItem().getTitle(), mapEvent.getItem().getUID());
                }else if(mapEvent.getItem() instanceof DrawingCircle){
                    DrawingCircle circle = (DrawingCircle) mapEvent.getItem();
                    addDrawnShape(mapEvent.getItem().getUID(), circle);
                    addDrawnShapeMarker(circle.getCenterMarker(), mapEvent.getItem().getUID());
                    ShapeNameManager.getInstance().addPolygonName(mapEvent.getItem().getTitle(), mapEvent.getItem().getUID());
                }else if(mapEvent.getItem() instanceof DrawingShape){
                    DrawingShape polyline = (DrawingShape) mapEvent.getItem();
                    GeoPoint lastPoint = polyline.getPoints()[0];
                    boolean isValid = true;
                    for(GeoPoint geoPoint : polyline.getPoints()){
                        if(geoPoint != lastPoint){
                            double distance = lastPoint.distanceTo(geoPoint);

                              if(distance < Constants.MINIMUM_POLYLINE_POINT_DISTANCE_METERS){
                                  isValid = false;
                                  break;
                              }
                              lastPoint = geoPoint;
                        }
                    }
                    if(isValid) {
                        addDrawnShape(mapEvent.getItem().getUID(), polyline);
                        ShapeNameManager.getInstance().addPolygonName(mapEvent.getItem().getTitle(), mapEvent.getItem().getUID());
                    }
                }else if(mapEvent.getItem() instanceof Marker){
                    String region = CachedKMLPlacemarkInfo.getInstance().getCurrentRegion();
                    Log.d(TAG, "onMapEvent!!: 1 " + mapEvent.getItem().getTitle());
                    if(region != null){
                        Log.d(TAG, "onMapEvent!!: 2 " + mapEvent.getItem().getTitle());
                        Set<String> set = PlacemarkLabelCreator.getInstance().getMapItemLabelsGenerated(CachedKMLPlacemarkInfo.getInstance().getCurrentRegion());
                        if(set != null && set.contains(mapEvent.getItem().getTitle())){
                            Log.d(TAG, "onMapEvent!!: 3 " + mapEvent.getItem().getTitle());
                            boolean labelVisibility = true;
                            mapEvent.getItem().setVisible(labelVisibility);
                            placemarkLabelToMapItem.put(mapEvent.getItem().getTitle(), (Marker) mapEvent.getItem());
                            allObjects.add(mapEvent.getItem().getTitle());
                        }
                    }else {
                        Marker marker = (Marker) mapEvent.getItem();
                        String shapeUid = ShapeNameManager.getInstance().shapeUidForName(marker.getTitle());
                        if (shapeUid != null) {
                            addDrawnShapeMarker(marker, shapeUid);
                        }
                    }
                }

            }
    }

    public void toggleLabelsBasedOnZoom(){
       //if(drawing){
       //     return;
        //}
        //Log.d(TAG, "Updating labels based on zoom");

        /* TODO: better understand how to calculate zoom factor, this seems to work well
         *  (is calculated using similar factor found in ATAK source code) */
        //double zoomFactor = MapView.getMapView().mapResolutionAsMapScale(MapView.getMapView().getMapResolution());
        //toggleLabels(zoomFactor > (2.0 / 10000.0), zoomFactor > 2 * (2.0 / 10000.0));
    }

    public void toggleDrawingMode(boolean drawing){
        this.drawing = drawing;
        if(drawing){
            toggleLabels(false, false);
        }else{
            toggleLabels(true, true);
        }
    }

    private void toggleLabels(boolean buildingsVisible, boolean objectsVisible){
        // if show building and object labels setting is toggled off, do nothing
        if(!allBuildingAndObjectLabelsEnabled){
            return;
        }

        this.buildingLabelsVisible = buildingsVisible;
        this.objectLabelsVisible = objectsVisible;
        for(Map.Entry<String, Marker> i : placemarkLabelToMapItem.entrySet()){
            // is enterable
            boolean isEnterable = CachedKMLPlacemarkInfo.getInstance().placemarkIsEnterable(i.getKey());

            // is a building
            if(isEnterable){
                if(MapView.getMapView().getBounds().contains(i.getValue().getPoint())) {
                    i.getValue().setVisible(buildingsVisible);
                    //i.getValue().setTouchable(buildingsVisible);
                    i.getValue().setClickable(buildingsVisible);
                }else{
                    i.getValue().setVisible(false);
                    //i.getValue().setTouchable(false);
                    i.getValue().setClickable(false);
                }
            }else{
                if(MapView.getMapView().getBounds().contains(i.getValue().getPoint())) {
                    i.getValue().setVisible(objectsVisible);
                    //i.getValue().setTouchable(objectsVisible);
                    i.getValue().setClickable(objectsVisible);
                }else{
                    i.getValue().setVisible(false);
                    //i.getValue().setTouchable(false);
                    i.getValue().setClickable(false);
                }
            }

            // don't hide selected objects
            if(selectedObjects.contains(i.getKey())) {
                i.getValue().setVisible(true);
                //i.getValue().setTouchable(true);
                i.getValue().setClickable(true);
            }
        }
    }

    public Marker getPlacemarkMapItemForLabel(String label){
        return placemarkLabelToMapItem.get(label);
    }

    public boolean buildingLabelsVisible(){
        return buildingLabelsVisible;
    }

    public boolean objectLabelsVisible(){
        return objectLabelsVisible;
    }

    public boolean isConstructingCommand() {
        return isConstructingCommand;
    }

    public void setConstructingCommand(boolean constructingCommand) {
        isConstructingCommand = constructingCommand;
    }

    public void addSelectedEntityListener(EntityListUpdatedListener entityListUpdatedListener){
        entityListUpdatedListeners.add(entityListUpdatedListener);
    }

    public void removeSelectedEntityListener(EntityListUpdatedListener entityListUpdatedListener){
        entityListUpdatedListeners.remove(entityListUpdatedListener);
    }

    public void toggleAprilTags(boolean visible) {
        aprilTagsVisible = visible;
        for (Map.Entry<String, Marker> entry : uidToATMarker.entrySet()) {
            entry.getValue().setVisible(visible);
        }
    }

    public Marker getAprilTagMapItemForLabel(String label){
        return uidToATMarker.get(label);
    }

    public void toggleBuildingAndObjectLabels(boolean visible) {
        allBuildingAndObjectLabelsEnabled = visible;
        if(visible) {
            toggleLabelsBasedOnZoom();
        }else{
            for (Map.Entry<String, Marker> entry : placemarkLabelToMapItem.entrySet()) {
                entry.getValue().setVisible(false);
            }
        }
    }

    public void addDrawnShape(String uid, Shape shape){
        shapeUids.put(uid, shape);
    }

    public Shape getDrawnShape(String uid){
        return shapeUids.get(uid);
    }

    public Set<String> getDrawnShapes(){
        return shapeUids.keySet();
    }

    public Collection<Shape> getAllShapes(){
        return shapeUids.values();
    }

    public Collection<Marker> getAllPoints(){
        return uidToPointDrawnMarker.values();
    }

    public void addSelectedShape(Shape shape){
        /*
        String shapeType = shape.getMetaString(Constants.SHAPE_TYPE_META_ATTR_NAME,
                Constants.SHAPE_TYPE.UNSPECIFIED.toString());
        int color = Constants.DEFAULT_SHAPE_COLOR_SELECTED;
        int strokeColor = Constants.SELECTED_SHAPE_STROKE_COLOR;
        if(shapeType.contentEquals(Constants.SHAPE_TYPE.WATCH.toString())){
            color = Constants.WATCH_SHAPE_COLOR_SELECTED;
            watchShapeSelected = shape.getUID();
        }else if(shapeType.contentEquals(Constants.SHAPE_TYPE.GOTO.toString())){
            color = Constants.GOTO_SHAPE_COLOR_SELECTED;
            gotoShapeSelected = shape.getUID();
        }else{
            unspecifiedShapeSelected = shape.getUID();
        }

        Log.d(TAG, "adding selected shape: " + shape.getTitle());
        selectedShapes.add(shape);
        shape.setColor(color);
        shape.setStrokeColor(strokeColor);
        int alpha = Constants.SELECTED_SHAPE_FILL_OPACITY;
        int fillColor = Color.argb(alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
        shape.setFillColor(fillColor);

        Marker marker = shapeUidToMarker.get(shape.getUID());
        if(marker != null){
            marker.setTextColor(Constants.MAP_OBJECT_SELECTED_TEXT_COLOR);
        }

        synchronized (shapeSelectedListeners) {
            for (ShapeSelectedListener listener : shapeSelectedListeners) {
                listener.shapeSelected(shape.getUID(), shapeType);
            }
        }*/
    }

    public void removeSelectedShape(Shape shape){
        /*
        String shapeType = shape.getMetaString(Constants.SHAPE_TYPE_META_ATTR_NAME,
                Constants.SHAPE_TYPE.UNSPECIFIED.toString());
        int color = Constants.DEFAULT_SHAPE_COLOR;
        int strokeColor = Constants.UNSELECTED_SHAPE_STROKE_COLOR;
        if(shapeType.contentEquals(Constants.SHAPE_TYPE.WATCH.toString())){
            color = Constants.WATCH_SHAPE_COLOR;
            watchShapeSelected = null;
        }else if(shapeType.contentEquals(Constants.SHAPE_TYPE.GOTO.toString())){
            color = Constants.GOTO_SHAPE_COLOR;
            gotoShapeSelected = null;
        }else{
            unspecifiedShapeSelected = null;
        }

        selectedShapes.remove(shape);
        shape.setColor(color);
        shape.setStrokeColor(strokeColor);
        int alpha = Constants.UNSELECTED_SHAPE_FILL_OPACITY;
        int fillColor = Color.argb(alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
        shape.setFillColor(fillColor);

        Marker marker = shapeUidToMarker.get(shape.getUID());
        if(marker != null){
            marker.setTextColor(Constants.MAP_OBJECT_UNSELECTED_TEXT_COLOR);
        }

        synchronized (shapeSelectedListeners) {
            for (ShapeSelectedListener listener : shapeSelectedListeners) {
                listener.shapeUnselected(shape.getUID(), shapeType);
            }
        }*/
    }

    public Set<Shape> getSelectedShapes(){
        return selectedShapes;
    }

    public void addDrawnShapeMarker(Marker marker, String shapeUid) {
        markerUidToShapeUid.put(marker.getUID(), shapeUid);
        shapeUidToMarker.put(shapeUid, marker);
    }

    public String getShapeUidForMarkerUid(String uid){
        return markerUidToShapeUid.get(uid);
    }

    public void addDrawnPoint(String uid, Marker marker) {
        uidToPointDrawnMarker.put(uid, marker);
    }

    public Marker getMarkerForDrawnPointUid(String uid){
        return uidToPointDrawnMarker.get(uid);
    }

    public Marker getMarkerForTrackUid(String uid){
        return trackUidToMarker.get(uid);
    }

    public Collection<Marker> getSelectedPoints() {
        return selectedMarkers;
    }

    public void addSelectedPoint(Marker marker){
        /*String shapeType = marker.getMetaString(Constants.SHAPE_TYPE_META_ATTR_NAME,
                Constants.SHAPE_TYPE.UNSPECIFIED.toString());
        int color = Constants.SELECTED_SHAPE_STROKE_COLOR;
        if(shapeType.contentEquals(Constants.SHAPE_TYPE.WATCH.toString())){
            color = Constants.WATCH_SHAPE_COLOR_SELECTED;
            watchShapeSelected = marker.getUID();
        }else if(shapeType.contentEquals(Constants.SHAPE_TYPE.GOTO.toString())){
            color = Constants.GOTO_SHAPE_COLOR_SELECTED;
            gotoShapeSelected = marker.getUID();
        }else{
            unspecifiedShapeSelected = marker.getUID();
        }
        marker.setColor(color);
        marker.setTextColor(Constants.MAP_OBJECT_SELECTED_TEXT_COLOR);
        selectedMarkers.add(marker);
*/
    }

    public void removeSelectedPoint(Marker marker){
        /*
        String shapeType = marker.getMetaString(Constants.SHAPE_TYPE_META_ATTR_NAME,
                Constants.SHAPE_TYPE.UNSPECIFIED.toString());
        int color = Constants.UNSELECTED_SHAPE_STROKE_COLOR;
        if(shapeType.contentEquals(Constants.SHAPE_TYPE.WATCH.toString())){
            color = Constants.WATCH_SHAPE_COLOR;
            watchShapeSelected = null;
        }else if(shapeType.contentEquals(Constants.SHAPE_TYPE.GOTO.toString())){
            color = Constants.GOTO_SHAPE_COLOR;
            gotoShapeSelected = null;
        }else{
            unspecifiedShapeSelected = null;
        }
        marker.setColor(color);
        marker.setTextColor(Constants.MAP_OBJECT_UNSELECTED_TEXT_COLOR);
        selectedMarkers.remove(marker);*/
    }

    public String getWatchShapeSelected() {
        return watchShapeSelected;
    }

    public String getGotoShapeSelected() {
        return gotoShapeSelected;
    }

    public String getUnspecifiedShapeSelected() {
        return unspecifiedShapeSelected;
    }
}
