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

import static com.atakmap.android.takcad.routing.OpenRouteConstants.OPEN_ROUTE_API_KEY_ARG;
import static com.atakmap.android.takcad.routing.OpenRouteConstants.OPEN_ROUTE_DIRECTIONS_GET_URL;
import static com.atakmap.android.takcad.routing.OpenRouteConstants.OPEN_ROUTE_END_ARG;
import static com.atakmap.android.takcad.routing.OpenRouteConstants.OPEN_ROUTE_PROFILE_DRIVING_CAR;
import static com.atakmap.android.takcad.routing.OpenRouteConstants.OPEN_ROUTE_START_ARG;

import com.atakmap.coremap.log.Log;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OpenRouteFunctions {

    public static String TAG = OpenRouteFunctions.class.getSimpleName();

    public static void getDirections(String long1, String lat1, String long2, String lat2, String apiKey,
                                     OpenRouteDirectionResponse callback) {

        Log.d(TAG, "Trying to get directions from point to point...");

        Thread networkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client = new OkHttpClient();

                    HttpUrl.Builder urlBuilder
                            = HttpUrl.parse(
                            OPEN_ROUTE_DIRECTIONS_GET_URL +
                                    OPEN_ROUTE_PROFILE_DRIVING_CAR
                    ).newBuilder();
                    urlBuilder.addQueryParameter(OPEN_ROUTE_API_KEY_ARG, apiKey);
                    urlBuilder.addQueryParameter(OPEN_ROUTE_START_ARG, lat1 + "," + long1);
                    urlBuilder.addQueryParameter(OPEN_ROUTE_END_ARG, lat2 + "," + long2);

                    String url = urlBuilder.build().toString();

                    Request request = new Request.Builder()
                            .url(url)
                            .build();
                    Call call = client.newCall(request);
                    Response response = call.execute();
                    String body = response.body().string();

                    Log.d(TAG, "Got response: " + response);
                    Log.d(TAG, "Got response body: " + body);

                    ObjectMapper om = new ObjectMapper();
                    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    DirectionsResponsePojos.Root root = om.readValue(body, DirectionsResponsePojos.Root.class);

                    Log.d(TAG, "Parsed directions response: " + root);

                    callback.processDirections(root);

                } catch (IOException e) {
                    Log.e(TAG, "Exception with http get: " + e, e);
                }
            }
        });

        networkThread.start();
    }

}
