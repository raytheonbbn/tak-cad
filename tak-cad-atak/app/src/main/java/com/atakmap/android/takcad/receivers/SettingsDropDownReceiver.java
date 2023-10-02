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

package com.atakmap.android.takcad.receivers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.takcad.TabbedDropDownReceiver;
import com.atakmap.android.takcad.plugin.R;
import com.atakmap.android.takcad.routing.OpenRouteApiManager;
import com.atakmap.android.takcad.util.MiscUtils;
import com.atakmap.coremap.log.Log;

public class SettingsDropDownReceiver extends TabbedDropDownReceiver implements
        DropDown.OnStateListener {

    public static final String TAG = SettingsDropDownReceiver.class
            .getSimpleName();

    public static final String SHOW_PLUGIN = SettingsDropDownReceiver.class.getSimpleName() + "SHOW_PLUGIN";
    private View templateView;
    private Context pluginContext;

    /**************************** CONSTRUCTOR *****************************/

    public SettingsDropDownReceiver(MapView mapView,
                                    final Context context) {
        super(mapView);
        this.pluginContext = context;

        templateView = PluginLayoutInflater.inflate(context,
                R.layout.settings_layout, null);

        setupTabs(context, templateView);

    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
        removeTabsListener(templateView);
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "SettingsDropDownReceiver invoked");

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {
            Log.d(TAG, "showing plugin drop down");

            selectTab(templateView, ownedTabIndex());

            EditText openApiKeyEditText = (EditText) templateView.findViewById(R.id.openApiKeyEditText);
            // Retrieve the saved value

            TextView openApiKeyDisplay = (TextView) templateView.findViewById(R.id.orsApiKeyDisplay);
            if (!OpenRouteApiManager.getInstance().getOpenRouteApiKey().isEmpty()) {
                openApiKeyDisplay.setText("API Key Set!");
                openApiKeyDisplay.setTextColor(Color.GREEN);
            } else {
                openApiKeyDisplay.setText("API Key Not Set!");
                openApiKeyDisplay.setTextColor(Color.RED);
            }



            Button openApiKeyUpdateButton = (Button) templateView.findViewById(R.id.updateOpenApiKeyButton);
            openApiKeyUpdateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    OpenRouteApiManager.getInstance().setOpenRouteApiKey(openApiKeyEditText.getText().toString());
                    openApiKeyDisplay.setText("API Key Set!");
                    openApiKeyDisplay.setTextColor(Color.GREEN);
                    MiscUtils.toast("Set Open Route API Key!");
                }
            });

            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

        }
    }

    @Override
    protected int ownedTabIndex() {
        return 2;
    }

    @Override
    public void onDropDownSelectionRemoved() {

    }

    @Override
    public void onDropDownClose() {

    }

    @Override
    public void onDropDownSizeChanged(double v, double v1) {

    }

    @Override
    public void onDropDownVisible(boolean b) {

    }

}
