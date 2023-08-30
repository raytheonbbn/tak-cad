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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.plugin.R;

public class OpenRouteApiManager {

    public static OpenRouteApiManager INSTANCE;

    public static final String OPEN_ROUTE_SERVICE_API_KEY = "TAKCAD_OPEN_ROUTE_SERVICE_API_KEY";

    SharedPreferences sharedPref = null;

    private OpenRouteApiManager(Context context) {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void instantiate() {
        INSTANCE = new OpenRouteApiManager(MapView.getMapView().getContext());
    }

    public static OpenRouteApiManager getInstance() {
        return INSTANCE;
    }

    public String getOpenRouteApiKey() {
        return sharedPref.getString(OPEN_ROUTE_SERVICE_API_KEY, "");
    }

    public void setOpenRouteApiKey(String key) {
        // Get an editor to modify the preferences
        SharedPreferences.Editor editor = sharedPref.edit();

        // Save a value using the key
        editor.putString(OPEN_ROUTE_SERVICE_API_KEY, key);

        // Commit the changes
        editor.apply();

    }

}
