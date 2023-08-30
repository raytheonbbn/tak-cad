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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ShapeNameManager {
    private static ShapeNameManager instance;
    private final Map<String, String> shapeNameToUid = new HashMap<>();
    private final Map<String, String> shapeUidToName = new HashMap<>();
    private final Set<String> polygonNames = new HashSet<>();
    private final Set<String> lineNames = new HashSet<>();
    private final Set<String> pointNames = new HashSet<>();

    private int polygonCounter = 0;
    private int lineCounter = 0;
    private int pointCounter = 0;

    public static ShapeNameManager getInstance(){
        if(instance == null){
            instance = new ShapeNameManager();
        }
        return instance;
    }

    private ShapeNameManager(){}

    public void removeRecordedName(String shapeName){
        String uid = shapeNameToUid.remove(shapeName);
        if(uid != null){
            if(polygonNames.remove(shapeName)){
                polygonCounter--;
            }else if(lineNames.remove(shapeName)){
                lineCounter--;
            }else if(pointNames.remove(shapeName)){
                pointCounter--;
            }
            shapeUidToName.remove(uid);
        }
    }

    public String generatePolygonName(String uid) {
        String name = "polygon_" + (polygonCounter + 1);
        shapeNameToUid.put(name, uid);
        shapeUidToName.put(uid, name);
        polygonCounter++;
        polygonNames.add(name);
        return name;
    }

    public String generatePointName(String uid) {
        String name = "point_" + (pointCounter + 1);
        shapeNameToUid.put(name, uid);
        shapeUidToName.put(uid, name);
        pointCounter++;
        pointNames.add(name);
        return name;
    }

    public String generateLineName(String uid) {
        String name = "line_" + (lineCounter + 1);
        shapeNameToUid.put(name, uid);
        shapeUidToName.put(uid, name);
        lineCounter++;
        lineNames.add(name);
        return name;
    }

    public void addPolygonName(String name, String uid){
        polygonCounter++;
        polygonNames.add(name);
        shapeNameToUid.put(name, uid);
        shapeUidToName.put(uid, name);
    }

    public boolean contains(String title) {
        return polygonNames.contains(title) || lineNames.contains(title) || pointNames.contains(title);
    }

    public String shapeUidForName(String title){
        return shapeNameToUid.get(title);
    }

    public String getNameForShapeUUID(String watchShapeSelected) {
        return shapeUidToName.get(watchShapeSelected);
    }
}
