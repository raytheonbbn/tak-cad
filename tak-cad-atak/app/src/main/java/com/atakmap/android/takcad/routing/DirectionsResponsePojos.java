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

package com.atakmap.android.takcad.routing;

import java.util.ArrayList;
import java.util.Date;

public class DirectionsResponsePojos {

    public static class Engine{
        public Engine() {
        }

        public String version;
        public Date build_date;
        public Date graph_date;

        @Override
        public String toString() {
            return "Engine{" +
                    "version='" + version + '\'' +
                    ", build_date=" + build_date +
                    ", graph_date=" + graph_date +
                    '}';
        }
    }

    public static class Feature{
        public Feature() {
        }

        public ArrayList<Double> bbox;
        public String type;
        public Properties properties;
        public Geometry geometry;

        @Override
        public String toString() {
            return "Feature{" +
                    "bbox=" + bbox +
                    ", type='" + type + '\'' +
                    ", properties=" + properties +
                    ", geometry=" + geometry +
                    '}';
        }
    }

    public static class Geometry{
        public Geometry() {
        }

        public ArrayList<ArrayList<Double>> coordinates;
        public String type;

        @Override
        public String toString() {
            return "Geometry{" +
                    "coordinates=" + coordinates +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public static class Metadata{
        public Metadata() {
        }

        public String attribution;
        public String service;
        public long timestamp;
        public Query query;
        public Engine engine;

        @Override
        public String toString() {
            return "Metadata{" +
                    "attribution='" + attribution + '\'' +
                    ", service='" + service + '\'' +
                    ", timestamp=" + timestamp +
                    ", query=" + query +
                    ", engine=" + engine +
                    '}';
        }
    }

    public static class Properties{
        public Properties() {
        }

        public ArrayList<Segment> segments;
        public Summary summary;
        public ArrayList<Integer> way_points;

        @Override
        public String toString() {
            return "Properties{" +
                    "segments=" + segments +
                    ", summary=" + summary +
                    ", way_points=" + way_points +
                    '}';
        }
    }

    public static class Query{
        public Query() {
        }

        public ArrayList<ArrayList<Double>> coordinates;
        public String profile;
        public String format;

        @Override
        public String toString() {
            return "Query{" +
                    "coordinates=" + coordinates +
                    ", profile='" + profile + '\'' +
                    ", format='" + format + '\'' +
                    '}';
        }
    }

    public static class Root{
        public Root() {
        }

        public String type;
        public ArrayList<Feature> features;
        public ArrayList<Double> bbox;
        public Metadata metadata;

        @Override
        public String toString() {
            return "Root{" +
                    "type='" + type + '\'' +
                    ", features=" + features +
                    ", bbox=" + bbox +
                    ", metadata=" + metadata +
                    '}';
        }
    }

    public static class Segment{
        public Segment() {
        }

        public double distance;
        public double duration;
        public ArrayList<Step> steps;

        @Override
        public String toString() {
            return "Segment{" +
                    "distance=" + distance +
                    ", duration=" + duration +
                    ", steps=" + steps +
                    '}';
        }
    }

    public static class Step{
        public Step() {
        }

        public double distance;
        public double duration;
        public int type;
        public String instruction;
        public String name;
        public ArrayList<Integer> way_points;

        @Override
        public String toString() {
            return "Step{" +
                    "distance=" + distance +
                    ", duration=" + duration +
                    ", type=" + type +
                    ", instruction='" + instruction + '\'' +
                    ", name='" + name + '\'' +
                    ", way_points=" + way_points +
                    '}';
        }
    }

    public static class Summary{
        public Summary() {
        }

        public double distance;
        public double duration;

        @Override
        public String toString() {
            return "Summary{" +
                    "distance=" + distance +
                    ", duration=" + duration +
                    '}';
        }
    }

}
