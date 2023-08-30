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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.takcad.Constants;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.routing.DirectionsResponsePojos;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Created by bentoll on 12/13/17.
 */

public class MiscUtils {
    private static final String TAG = MiscUtils.class.getName();
    private static String activeDropDown;

    public static double convertSecondsToMinutes(double durationInSeconds) {
        return durationInSeconds / 60;
    }

    public static String convertMinutesToTimeString(double minutes) {
        int wholeMinutes = (int) minutes;
        int remainingSeconds = (int) ((minutes - wholeMinutes) * 60);

        // Format minutes
        String minutesString = String.format("%02d", wholeMinutes);

        // Format seconds
        String secondsString = String.format("%02d", remainingSeconds);

        return minutesString + ":" + secondsString + " (min/s)";
    }

    public static DrawingShape convertDirectionsPojoToDrawingShape(DirectionsResponsePojos.Root directions,
                                                                   MapGroup drawingGroup) {

        Log.d(TAG, "Got directions pojo: " + directions);

        DrawingShape drawingShape = new DrawingShape(MapView.getMapView(), drawingGroup, UUID.randomUUID().toString());

        List<GeoPoint> geoPointList = new ArrayList<GeoPoint>();
        if (directions != null && directions.features != null && directions.features.get(0) != null) {
            for (List<Double> coordinate : directions.features.get(0).geometry.coordinates) {
                geoPointList.add(new GeoPoint(coordinate.get(1), coordinate.get(0)));
            }
        } else {
            return null;
        }

        GeoPoint[] geoPointArray = new GeoPoint[geoPointList.size()];
        for (int i = 0; i < geoPointArray.length; i++) {
            geoPointArray[i] = geoPointList.get(i);
        }
        drawingShape.setPoints(geoPointArray);
        drawingShape.setClosed(false);

        return drawingShape;

    }

    public static String parseXml(String input) {
        try {

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput( new StringReader( input ) ); // pass input whatever xml you have
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_DOCUMENT) {
                    Log.v(TAG,"Start document");
                } else if(eventType == XmlPullParser.START_TAG) {
                    Log.v(TAG,"Start tag "+xpp.getName());
                } else if(eventType == XmlPullParser.END_TAG) {
                    Log.v(TAG,"End tag "+xpp.getName());
                } else if(eventType == XmlPullParser.TEXT) {
                    Log.v(TAG,"Text "+xpp.getText()); // here you get the text from xml
                    return xpp.getText();
                }
                eventType = xpp.next();
            }
            Log.v(TAG,"End document");

        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Exception parsing XML: ", e);
        }
        return null;
    }

    public static long dateTimeStringToUnixTime(String date, String time) {
        String [] timeFields = time.split(":");
        int hour = 0;
        int minute = 0;
        if (timeFields.length != 2) {
            Log.w(TAG, "Invalid time '" + time + "', so setting last seen time to 0");
        } else {
            hour = Integer.valueOf(timeFields[0]);
            minute = Integer.valueOf(timeFields[1]);
        }

        String [] dateFields = date.split("/");
        int month = 0;
        int day = 0;
        int year = 0;
        if (dateFields.length != 3) {
            Log.w(TAG, "Invalid date '" + date + "', so setting date to default");
        } else {
            // In Java, months start at 0
            month = Integer.valueOf(dateFields[0]) - 1;
            day = Integer.valueOf(dateFields[1]);
            year = Integer.valueOf(dateFields[2]);
        }

        // ** Use the time zone where the program is currently running.
        Calendar calendar = new GregorianCalendar(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }

    public static void toast(String str) {
        Toast.makeText(MapView.getMapView().getContext(), str,
                Toast.LENGTH_LONG).show();
    }

    public static void notify(Context context, String contentTitle, String contentText) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(NotificationUtil.GeneralIcon.ATAK.getID())
                .setContentTitle(contentTitle)
                .setContentText(contentText);
        Notification notification = new Notification.BigTextStyle(builder)
                .bigText(contentText)
                .build();
        notificationManager.notify(1, notification);
    }

    public static boolean validateIpv4Address(String input) {
        Pattern pattern = Pattern.compile("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.|$)){4}");
        return pattern.matcher(input).matches();
    }

    public static boolean validatePort(String port) {
        if (port == null) {
            return false;
        }

        try {
            int intPort = Integer.parseInt(port);
            return intPort >= 0 && intPort <= 65535;
        } catch (NumberFormatException e){
            return false;
        }

    }


    public static int getScreenWidthPx() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeightPx() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    public static float getScreenXdpi() {
        return Resources.getSystem().getDisplayMetrics().xdpi;
    }

    public static float getScreenYdpi() {
        return Resources.getSystem().getDisplayMetrics().ydpi;
    }

    public static float getDensity() {
        return Resources.getSystem().getDisplayMetrics().density;
    }

    /**
     * Adapted from:
     * https://stackoverflow.com/questions/4275797/view-setpadding-accepts-only-in-px-is-there-anyway-to-setpadding-in-dp
     */
    public static int dpTopx(Context context, int dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }


    public static String roundOffTo2DecPlaces(double val) {
        return String.format("%.2f", val);
    }

    public static void disableOverlayInteraction(boolean selectableOff, MapView mapView){
        if(selectableOff) {
            mapView.getMapTouchController().lockControls();
        }else{
            mapView.getMapTouchController().unlockControls();
        }
    }

    /**
     * Inspired by ImportAlternateContactSort
     * @return the callsign associated with your device
     */
    public static String getMyCallsign() {
        return MapView.getMapView().getDeviceCallsign().toLowerCase(LocaleUtil.getCurrent());
    }

    public static String truncateName(String name) {
        return (name.length() > 20) ? name.substring(0, 17) + "..." : name;
    }

    public static String getFormattedDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return dateFormat.format(date);
    }

    public static void logActiveDropDown(String name){
        Log.d(TAG, "ACTIVE_DROPDOWN: " + name);
        activeDropDown = name;
    }

    public static String getActiveDropDown(){
        return activeDropDown;
    }

    public static boolean isActiveDropDown(String canonicalClassName){
        if(activeDropDown == null || canonicalClassName == null){
            return false;
        }
        else {
            return activeDropDown.contentEquals(
                    canonicalClassName);
        }
    }

    public static void deleteShape(MapView view, String uid) {
        Log.v(TAG, "Deleting shape with UUID: " + uid);
        MapItem victim = view.getRootGroup().deepFindUID(uid);
        if (victim == null) {
            Log.e(TAG, "Unable to delete shape; no shape with UID: " + uid);
        } else {
            victim.getGroup().removeItem(victim);
        }
    }

    /**
     * Round to certain number of decimals
     *
     * @param d
     * @param decimalPlace
     * @return float rounded number
     */
    public static double round(double d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.doubleValue();
    }

    public static double feetToMeters(double feet){
        return feet * Constants.FEET_METERS_RATIO;
    }

    public static double metersToFeet(double meters){
        return meters / Constants.FEET_METERS_RATIO;
    }
}
