
package com.atakmap.android.takcad.plugin;

import static android.graphics.Color.WHITE;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.gui.PluginSpinner;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.takcad.Constants;
import com.atakmap.android.takcad.IncidentResponderManager;
import com.atakmap.android.takcad.point_entry.persistence.MapStateListener;
import com.atakmap.android.takcad.point_entry.persistence.ShapeNameManager;
import com.atakmap.android.takcad.point_entry.shapes.PointCreatorCustom;
import com.atakmap.android.takcad.point_entry.shapes.ShapeCallback;
import com.atakmap.android.takcad.point_entry.shapes.ShapeCreator;
import com.atakmap.android.takcad.routing.DirectionsResponsePojos;
import com.atakmap.android.takcad.routing.OpenRouteApiManager;
import com.atakmap.android.takcad.routing.OpenRouteDirectionResponse;
import com.atakmap.android.takcad.routing.OpenRouteFunctions;
import com.atakmap.android.takcad.util.CotUtil;
import com.atakmap.android.takcad.util.MiscUtils;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class PluginTemplate implements IPlugin {

    public static final String TAG = PluginTemplate.class.getSimpleName();

    private String latitude = "";
    private String longitude = "";
    private String title = "";
    private String summary = "";

    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    View incidentCreatorView;
    View activeIncidentViewerView;
    View settingsView;
    Pane incidentCreatorPane;
    Pane activeIncidentViewerPane;
    Pane settingsPane;

    private List<String> expandableTitleList = null;
    Map<String, List<IncidentResponderManager.ResponderInfo>> expandableDetailList;
    IncidentRespondersListAdapter expandableListAdapter;

    private Handler handler;

    private static final int MSG_UPDATE_LIST = 0;
    private static final int UPDATE_LIST_INTERVAL = 500;

    public PluginTemplate(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        // obtain the UI service
        uiService = serviceController.getService(IHostUIService.class);

        // initialize the toolbar button for the plugin

        // create the button
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                })
                .build();
    }

    @Override
    public void onStart() {
        // the plugin is starting, add the button to the toolbar
        if (uiService == null)
            return;

        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);
    }

    private void showPane() {
        // instantiate the plugin view if necessary
        if(incidentCreatorPane == null) {
            // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
            // In this case, using it is not necessary - but I am putting it here to remind
            // developers to look at this Inflator

            OpenRouteApiManager.instantiate();

            IncidentResponderManager.getInstance().start();

            instantiateIncidentCreator();
            instantiateActiveIncidentViewer();
            instantiateSettings();

            CotUtil.setCotEventListener(event -> {

                Log.d(TAG, "onCotEvent (util): " + event);

                if (event.getHow().equals("tak-cad-incident")) {

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Got TAK CAD incident.");

                            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                            Map<String, String> incidentMetaData = new Gson().fromJson(MiscUtils.parseXml(event.getDetail().toString()), mapType);
                            String incidentId = incidentMetaData.get("incident_id");

                            String latitude = incidentMetaData.get("latitude");
                            String longitude = incidentMetaData.get("longitude");
                            String title = incidentMetaData.get("title");
                            String summary = incidentMetaData.get("summary");

                            Log.d(TAG, "Got coordinates of incident: " + longitude + "," + latitude);

                            String myLatitude = Double.toString(MapView.getMapView().getSelfMarker().getPoint().getLatitude());
                            String myLongitude = Double.toString(MapView.getMapView().getSelfMarker().getPoint().getLongitude());

                            Log.d(TAG, "Got my coordinates: " + myLongitude + "," + myLatitude);

                            String openRouteApiKey = OpenRouteApiManager.getInstance().getOpenRouteApiKey();
                            if (openRouteApiKey == null || openRouteApiKey.isEmpty()) {
                                MiscUtils.toast("TAK CAD: Please Set Your Open Route Service API Key in Settings!");
                                return;
                            }

                            OpenRouteFunctions.getDirections(myLatitude, myLongitude, latitude, longitude, openRouteApiKey,
                                    new OpenRouteDirectionResponse() {
                                        @Override
                                        public void processDirections(DirectionsResponsePojos.Root response) {
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {

                                                    if (response != null && response.features != null && response.features.get(0) != null) {

                                                        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MapView.getMapView().getContext());
                                                        dialogBuilder.setTitle("New Incident: Do you want to respond?");

                                                        DirectionsResponsePojos.Feature feature = response.features.get(0);
                                                        double durationInMinutes =
                                                                MiscUtils.convertSecondsToMinutes(feature.properties.summary.duration);
                                                        String minutesTimeString = MiscUtils.convertMinutesToTimeString(durationInMinutes);

                                                        dialogBuilder.setMessage(
                                                                "Incident Title: " + title + "\n" +
                                                                        "Incident Summary: " + summary + "\n" +
                                                                        "ETA: " + minutesTimeString
                                                        );
                                                        dialogBuilder.setPositiveButton("Yes", (DialogInterface dialog, int which) -> {
                                                            Log.d(TAG, "Responding to incident...");

                                                            CotEvent cotEvent = new CotEvent();
                                                            cotEvent.setUID(MapView.getDeviceUid());
                                                            cotEvent.setType("a-f-G-U-C");

                                                            Map<String, String> responseMetaData = new HashMap<>();

                                                            Gson gson = new Gson();

                                                            responseMetaData.put("callsign", MapView.getMapView().getDeviceCallsign());
                                                            responseMetaData.put("incident_id", incidentId);
                                                            responseMetaData.put("title", title);
                                                            responseMetaData.put("summary", summary);
                                                            responseMetaData.put("latitude", latitude);
                                                            responseMetaData.put("longitude", longitude);
                                                            responseMetaData.put("version", "0");
                                                            try {
                                                                responseMetaData.put("directions", new ObjectMapper().writeValueAsString(response));
                                                            } catch (JsonProcessingException e) {
                                                                Log.e(TAG, "Error with getting directions json string", e);
                                                            }
                                                            CotDetail detailElement = new CotDetail();
                                                            CotDetail takCadElement = new CotDetail();
                                                            takCadElement.setInnerText(gson.toJson(responseMetaData));
                                                            detailElement.addChild(takCadElement);

                                                            cotEvent.setDetail(detailElement);

                                                            CoordinatedTime coordinatedTime = new CoordinatedTime(
                                                                    System.currentTimeMillis());
                                                            cotEvent.setTime(coordinatedTime);
                                                            cotEvent.setStart(coordinatedTime);
                                                            cotEvent.setStale(coordinatedTime);

                                                            cotEvent.setHow("tak-cad-response");

                                                            Log.d(TAG, "Generated tak cad response: " + cotEvent);

                                                            CotUtil.sendCotMessage(cotEvent);

                                                            IncidentResponderManager.IncidentInfo incidentInfo = new IncidentResponderManager.IncidentInfo();
                                                            incidentInfo.latitude = latitude;
                                                            incidentInfo.longitude = longitude;
                                                            incidentInfo.summary = summary;
                                                            incidentInfo.title = title;

                                                            IncidentResponderManager.getInstance().addIncidentInfo(incidentId, incidentInfo);
                                                            IncidentResponderManager.getInstance().addMyResponseVersion(incidentId, 0);

                                                            dialog.dismiss();

                                                        });
                                                        dialogBuilder.setNegativeButton("No", (DialogInterface dialog, int which) -> {
                                                            Log.d(TAG, "Ignoring incident.");
                                                            dialog.dismiss();
                                                        });
                                                        dialogBuilder.setCancelable(false);
                                                        AlertDialog dialog = dialogBuilder.create();
                                                        dialog.setCanceledOnTouchOutside(false);
                                                        dialog.show();

                                                    }
                                                }
                                            });
                                        }
                                    });
                        }
                    });

                } else if (event.getHow().equals("tak-cad-response")) {

                    Log.d(TAG, "Got TAK CAD response.");

                    Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> responseMetaData = new Gson().fromJson(MiscUtils.parseXml(event.getDetail().toString()), mapType);

                    String callSign = responseMetaData.get("callsign");
                    String incidentId = responseMetaData.get("incident_id");
                    String directionsJson = responseMetaData.get("directions");
                    String title = responseMetaData.get("title");
                    String summary = responseMetaData.get("summary");
                    String latitude = responseMetaData.get("latitude");
                    String longitude = responseMetaData.get("longitude");
                    String version = responseMetaData.get("version");

                    DirectionsResponsePojos.Root directions = null;

                    try {
                        directions = new ObjectMapper().readValue(directionsJson, DirectionsResponsePojos.Root.class);
                    } catch (JsonProcessingException e) {
                        Log.e(TAG, "Failed to parse directions pojo", e);
                    }

                    IncidentResponderManager.ResponderInfo responderInfo = new IncidentResponderManager.ResponderInfo();
                    responderInfo.callSign = callSign;
                    responderInfo.directions = directions;
                    responderInfo.version = Integer.parseInt(version != null ? version : "-1");

                    IncidentResponderManager.IncidentInfo incidentInfo = new IncidentResponderManager.IncidentInfo();
                    incidentInfo.latitude = latitude;
                    incidentInfo.longitude = longitude;
                    incidentInfo.title = title;
                    incidentInfo.summary = summary;

                    if (responderInfo.directions != null && responderInfo.directions.features != null && responderInfo.directions.features.get(0) != null) {
                        IncidentResponderManager.getInstance().addIncidentInfo(incidentId, incidentInfo);
                        IncidentResponderManager.getInstance().addResponderInfo(incidentId, responderInfo);
                    }

                    Log.d(TAG, "Current incident id to responder info map: " +
                            IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo());

                } else if (event.getHow().equals("tak-cad-response-update")) {

                    Log.d(TAG, "Got TAK CAD response update.");

                    Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> responseMetaData = new Gson().fromJson(MiscUtils.parseXml(event.getDetail().toString()), mapType);

                    String callSign = responseMetaData.get("callsign");
                    String incidentId = responseMetaData.get("incident_id");
                    String directionsJson = responseMetaData.get("directions");
                    String version = responseMetaData.get("version");

                    DirectionsResponsePojos.Root directions = null;

                    try {
                        directions = new ObjectMapper().readValue(directionsJson, DirectionsResponsePojos.Root.class);
                    } catch (JsonProcessingException e) {
                        Log.e(TAG, "Failed to parse directions pojo", e);
                    }

                    IncidentResponderManager.ResponderInfo responderInfo = new IncidentResponderManager.ResponderInfo();
                    responderInfo.callSign = callSign;
                    responderInfo.version = Integer.parseInt(version != null ? version : "-1");
                    responderInfo.directions = directions;

                    if (responderInfo.directions != null && responderInfo.directions.features != null && responderInfo.directions.features.get(0) != null) {
                        if (IncidentResponderManager.getInstance().getIncidentInfo(incidentId) != null) {
                            // only add the responder info if the incident info for the incident id isn't null
                            IncidentResponderManager.getInstance().addResponderInfo(incidentId, responderInfo);
                        }
                    }

                }
            });

        }


        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(incidentCreatorPane)) {
            uiService.showPane(incidentCreatorPane, null);
        }
    }

    private void instantiateIncidentCreator() {
        incidentCreatorView = PluginLayoutInflater.inflate(pluginContext,
                R.layout.incident_creator_layout, null);

        instantiateIncidentCreatorView(incidentCreatorView);

        incidentCreatorPane = new PaneBuilder(incidentCreatorView)
                // relative location is set to default; pane will switch location dependent on
                // current orientation of device screen
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                // pane will take up 50% of screen width in landscape mode
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                // pane will take up 50% of screen height in portrait mode
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                .build();
    }

    private void instantiateActiveIncidentViewer() {
        activeIncidentViewerView = PluginLayoutInflater.inflate(pluginContext,
                R.layout.incident_viewer_layout, null);

        instantiateActiveIncidentViewerView(activeIncidentViewerView);

        activeIncidentViewerPane = new PaneBuilder(activeIncidentViewerView)
                // relative location is set to default; pane will switch location dependent on
                // current orientation of device screen
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                // pane will take up 50% of screen width in landscape mode
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                // pane will take up 50% of screen height in portrait mode
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                .build();
    }

    private void instantiateSettings() {
        settingsView = PluginLayoutInflater.inflate(pluginContext,
                R.layout.settings_layout, null);

        instantiateSettingsView(settingsView);

        settingsPane = new PaneBuilder(settingsView)
                // relative location is set to default; pane will switch location dependent on
                // current orientation of device screen
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                // pane will take up 50% of screen width in landscape mode
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                // pane will take up 50% of screen height in portrait mode
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                .build();
    }

    private PluginSpinner generateSpinner(Context context) {
        PluginSpinner pluginSpinner = new PluginSpinner(context);
        LinearLayout.LayoutParams layoutParamsText = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140);
        layoutParamsText.setMargins(50, 20, 50, 20);
        pluginSpinner.setPadding(0, 0, 0, 0);
        pluginSpinner.setLayoutParams(layoutParamsText);
        pluginSpinner.setBackgroundColor(Color.GRAY);
        return pluginSpinner;
    }

    private void instantiateIncidentCreatorView(View templateView) {

        instantiateTabs(templateView);

        LinearLayout.LayoutParams layoutParamsButton = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                160);

        LinearLayout templateViewLayout = templateView.findViewById(R.id.scrollLayout);

        TextView incidentTitle = new TextView(pluginContext);
        incidentTitle.setTextSize(18f);
        incidentTitle.setTextColor(WHITE);
        incidentTitle.setText(R.string.incident_title);
        EditText incidentTitleEntry = new EditText(pluginContext);
        incidentTitleEntry.setTextColor(WHITE);
        incidentTitleEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int
                    count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                title = s.toString();
            }
        });
        templateViewLayout.addView(incidentTitle);
        templateViewLayout.addView(incidentTitleEntry);

        TextView incidentSummary = new TextView(pluginContext);
        incidentSummary.setTextSize(18f);
        incidentSummary.setTextColor(WHITE);
        incidentSummary.setText(R.string.incident_summary);
        EditText incidentSummaryEntry = new EditText(pluginContext);
        incidentSummaryEntry.setTextColor(WHITE);
        incidentSummaryEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int
                    count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                summary = s.toString();
            }
        });
        templateViewLayout.addView(incidentSummary);
        templateViewLayout.addView(incidentSummaryEntry);

        List<String> options = new ArrayList<>();
        options.add("-");

        Map<String, Marker> labelToMarker = new HashMap<>();

        PluginSpinner selectShapeSpinner = generateSpinner(pluginContext);
        ArrayAdapter<String> adp = new ArrayAdapter<>(pluginContext,
                android.R.layout.simple_spinner_dropdown_item,
                options
        );
        selectShapeSpinner.setAdapter(adp);
        selectShapeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (selectShapeSpinner.getSelectedItem().toString().contentEquals("-")) {
                    latitude = null;
                    longitude = null;
                    return;
                }

                Marker marker = labelToMarker.get(selectShapeSpinner.getSelectedItem().toString());
                if (marker != null) {
                    latitude = Double.toString(marker.getPoint().getLatitude());
                    longitude = Double.toString(marker.getPoint().getLongitude());
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        Button button = new Button(pluginContext);
        button.setText(R.string.incident_specify_location);
        button.setLayoutParams(layoutParamsButton);
        //TextPrompt container = TextPrompt.getInstance();
        button.setOnClickListener(view -> {
            int color = Constants.UNSELECTED_SHAPE_STROKE_COLOR;
            MapGroup drawingGroup = MapView.getMapView().getRootGroup().findMapGroup(
                    "Drawing Objects");
            MiscUtils.toast("Tap on the incident location.");
            ShapeCreator creator = new PointCreatorCustom(MapView.getMapView(), drawingGroup,
                    color,
                    "Point",
                    Constants.SHAPE_TYPE.WATCH.toString(),
                    new ShapeCallback<DrawingShape>() {
                        @Override
                        public void onShapeCreated(DrawingShape shape) {
                        }

                        @Override
                        public void onShapeCreated(Marker marker) {
                            marker.setAlwaysShowText(true);

                            marker.setTitle(ShapeNameManager.getInstance().generatePointName(marker.getUID()));

                            marker.setTextColor(Constants.MAP_OBJECT_UNSELECTED_TEXT_COLOR);
                            int color = Constants.UNSELECTED_SHAPE_STROKE_COLOR;
                            marker.setColor(color);
                            Log.d(TAG, "marker uuid: " + marker.getUID());
                            MapStateListener.getInstance().addDrawnPoint(marker.getUID(), marker);
                            MapStateListener.getInstance().addSelectedPoint(marker);

                            latitude = Double.toString(marker.getPoint().getLatitude());
                            longitude = Double.toString(marker.getPoint().getLongitude());

                            labelToMarker.put(marker.getTitle(), marker);

                            //container.closePrompt();

                            if (!options.contains(marker.getTitle())) {
                                options.add(marker.getTitle());
                                selectShapeSpinner.setSelection(options.size() - 1);
                            }
                        }
                    });
            creator.begin();

            //container.displayPrompt("Tap on the location of the incident.");
        });

        templateViewLayout.addView(button);
        templateViewLayout.addView(selectShapeSpinner);

        Button submitButton = templateView.findViewById(R.id.submitButton);
        submitButton.setOnClickListener(view -> {
            // make sure that all required inputs are there before submitting
            if (latitude == null || latitude.isEmpty() ||
                    longitude == null || longitude.isEmpty()) {
                Toast.makeText(MapView.getMapView().getContext(), "Please enter a location for the incident.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (title == null || title.isEmpty()) {
                Toast.makeText(MapView.getMapView().getContext(), "Please enter a title for the incident.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (summary == null || summary.isEmpty()) {
                Toast.makeText(MapView.getMapView().getContext(), "Please enter a summary for the incident.", Toast.LENGTH_SHORT).show();
                return;
            }

            CotEvent cotEvent = new CotEvent();
            cotEvent.setUID(MapView.getDeviceUid());
            cotEvent.setType("a-f-G-U-C");

            UUID incidentId = UUID.randomUUID();

            Map<String, String> metaData = new HashMap<>();
            metaData.put("latitude", latitude);
            metaData.put("longitude", longitude);
            metaData.put("callsign", MapView.getMapView().getDeviceCallsign());
            metaData.put("incident_id", incidentId.toString());
            metaData.put("title", title);
            metaData.put("summary", summary);

            Gson gson = new Gson();

            CotDetail detailElement = new CotDetail();
            CotDetail takCadElement = new CotDetail();
            takCadElement.setInnerText(gson.toJson(metaData));
            detailElement.addChild(takCadElement);

            cotEvent.setDetail(detailElement);

            CoordinatedTime coordinatedTime = new CoordinatedTime(
                    System.currentTimeMillis());
            cotEvent.setTime(coordinatedTime);
            cotEvent.setStart(coordinatedTime);
            cotEvent.setStale(coordinatedTime);

            cotEvent.setHow("tak-cad-incident");

            CotUtil.sendCotMessage(cotEvent);
        });
    }

    private void instantiateActiveIncidentViewerView(View templateView) {

        instantiateTabs(templateView);

        ExpandableListView expandableListView = (ExpandableListView) templateView.findViewById(
                R.id.expandable_list_view);

        Set<String> incidentIdList = IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo().keySet();

        expandableTitleList = new ArrayList<>(incidentIdList);
        expandableDetailList = IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo();

        expandableListAdapter = new IncidentRespondersListAdapter(pluginContext,
                expandableTitleList, expandableDetailList);
        expandableListView.setAdapter(expandableListAdapter);


        handler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_LIST: {

                        updateList();

                        Message updateListMsg = new Message();
                        updateListMsg.what = MSG_UPDATE_LIST;
                        handler.sendMessageDelayed(updateListMsg, UPDATE_LIST_INTERVAL);

                        break;
                    }
                }

                return true;
            }
        });

        Message updateListMsg = new Message();
        updateListMsg.what = MSG_UPDATE_LIST;
        handler.sendMessageDelayed(updateListMsg, UPDATE_LIST_INTERVAL);
    }

    private void instantiateSettingsView(View templateView) {

        instantiateTabs(templateView);

        EditText openApiKeyEditText = (EditText) templateView.findViewById(R.id.openApiKeyEditText);

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

    }

    private void instantiateTabs(View view) {
        TabLayout tabs = view.findViewById(R.id.main_tab_layout);
        tabs.addOnTabSelectedListener(listener);
    }

    private TabLayout.OnTabSelectedListener listener = new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            Log.d(TAG, "onTagSelected: Position = " + tab.getPosition());

            switch (tab.getPosition()) {
                case 0:
                    Log.i(TAG, "INCIDENT CREATOR");
                    selectTab(incidentCreatorView, 0);
                    uiService.showPane(incidentCreatorPane, null);

                    break;
                case 1:
                    Log.i(TAG, "INCIDENT VIEWER");
                    selectTab(activeIncidentViewerView, 1);
                    uiService.showPane(activeIncidentViewerPane, null);

                    break;
                case 2:
                    Log.i(TAG, "SETTINGS");
                    selectTab(settingsView, 2);
                    uiService.showPane(settingsPane, null);

                    break;
                default:
                    Log.e(TAG, "Unexpected tab entry encountered");
            }

        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
            // ** NO-OP
        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
            Log.d(TAG, "onTabReselected: Position = " + tab.getPosition());
            switch (tab.getPosition()) {
                case 0:
                    Log.v(TAG, "onTabReselected: INCIDENT CREATOR");
                    break;
                case 1:
                    Log.v(TAG, "onTabReselected: INCIDENT VIEWER");
                    break;
                case 2:
                    Log.v(TAG, "onTabReselected: SETTINGS");
                    break;
                default:
                    Log.e(TAG, "Unexpected tab entry encountered");
            }
        }
    };


    private void updateList() {
        expandableTitleList.clear();
        Set<String> incidentIdList = IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo().keySet();
        expandableTitleList.addAll(incidentIdList);

        expandableDetailList.clear();
        expandableDetailList.putAll(IncidentResponderManager.getInstance().getMyIncidentIdToResponderInfo());

        expandableListAdapter.notifyDataSetChanged();
    }

    private void selectTab(View v, int index) {
        TabLayout tabs = v.findViewById(R.id.main_tab_layout);
        TabLayout.Tab tab = tabs.getTabAt(index);
        if (tab == null) {
            Log.e(TAG, "tab at index "+index+" was null");
            return;
        }
        tab.select();
    }

}
