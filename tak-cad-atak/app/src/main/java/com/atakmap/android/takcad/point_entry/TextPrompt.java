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

package com.atakmap.android.takcad.point_entry;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget.OnClickListener;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.MapWidget2.OnWidgetSizeChangedListener;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.assets.Icon.Builder;

public class TextPrompt implements OnClickListener, Runnable, OnWidgetSizeChangedListener {
    public static final String TAG = TextPrompt.class.getCanonicalName();
    private static TextPrompt _instance;
    private static final int xIconAnchor = 0;
    private static final int yIconAnchor = 0;
    private static final int xSize = 32;
    private static final int ySize = 32;
    private final MapView _mapView = MapView.getMapView();
    private static final int DEFAULT_COLOR = -16711936;
    private final MapTextFormat DEFAULT_FORMAT;
    private final TextWidget _text;
    private final MarkerIconWidget _widget;
    private String _prompt = "";
    private Icon icon_lit;
    private Icon icon_unlit;
    private Icon icon_blank;
    private MapTextFormat textFormat = MapView.getDefaultTextFormat();
    private int color = -16711936;
    boolean displaying = false;
    private SharedPreferences _prefs;
    private View hint_view;
    private ClickCallback clickCallback;
    public interface ClickCallback{
        void clicked();
    }

    public TextPrompt() {
        this.DEFAULT_FORMAT = MapView.getTextFormat(Typeface.DEFAULT_BOLD, 4);
        RootLayoutWidget root = (RootLayoutWidget)this._mapView.getComponentExtra("rootLayoutWidget");
        LinearLayoutWidget topRight = root.getLayout(2);
        LinearLayoutWidget topEdge = root.getLayout(1);
        this._prefs = PreferenceManager.getDefaultSharedPreferences(this._mapView.getContext());
        this._text = new TextWidget("", this.textFormat);
        this._text.setName("Tooltip Text");
        this._text.setMargins(0.0F, 16.0F, 0.0F, 0.0F);
        this._text.setVisible(false);
        this._text.addOnClickListener((OnClickListener) (mapWidget, motionEvent) -> {
            if(clickCallback != null){
                clickCallback.clicked();
                clickCallback = null;
            }
        });
                topEdge.addWidget(this._text);

        topEdge.addOnWidgetSizeChangedListener(this);
        this._widget = new MarkerIconWidget();
        this._widget.setName("Tooltip Toggle");
        this._widget.setMargins(0.0F, 32.0F, 32.0F, 0.0F);
        this._widget.setVisible(false);
        topRight.addWidget(this._widget);
        this.icon_lit = this.createIcon(ATAKUtilities.getResourceUri(2131165428));
        this.icon_unlit = this.createIcon(ATAKUtilities.getResourceUri(2131165427));
        this.icon_blank = this.createIcon(ATAKUtilities.getResourceUri(2131165222));
        //this._widget.addOnClickListener(this);
        _instance = this;
    }

    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (this._widget.getIcon() != this.icon_blank) {
            if (this._widget.getIcon() == this.icon_unlit) {
                this._text.setVisible(true);
                this._widget.setIcon(this.icon_lit);
                this._prefs.edit().putBoolean("textContainer.textShowing", true).apply();
            } else {
                this._text.setVisible(false);
                this._widget.setIcon(this.icon_unlit);
                this._prefs.edit().putBoolean("textContainer.textShowing", false).apply();
            }
        }

    }

    public void onWidgetSizeChanged(MapWidget2 widget) {
        if (this.displaying) {
            this.displayPromptAtTop(this._prompt, false, () -> {
            });
        }

    }

    private Icon createIcon(String imageUri) {
        Builder builder = new Builder();
        builder.setAnchor(0, 0);
        builder.setColor(0, -1);
        builder.setSize(32, 32);
        builder.setImageUri(0, imageUri);
        return builder.build();
    }

    public static synchronized TextPrompt getInstance() {
        if (_instance == null) {
            _instance = new TextPrompt();
        }

        return _instance;
    }

    public static TextPrompt getTopInstance() {
        return getInstance();
    }

    public void dispose() {
        if (this._widget != null) {
            this._widget.removeOnClickListener(this);
        }

        _instance = null;
    }

    private void setTextFormat(MapTextFormat textFormat) {
        this.textFormat = textFormat;
        this._text.setTextFormat(textFormat);
    }

    public void displayPrompt(int textId) {
        this.displayPrompt(this._mapView.getContext().getString(textId));
    }

    public void displayPrompt(String prompt) {
        this.displayPrompt(prompt, this.DEFAULT_FORMAT, -16711936, null);
    }

    public void displayPrompt(String prompt, ClickCallback callback) {
        this.displayPrompt(prompt, this.DEFAULT_FORMAT, -16711936, callback);
    }

    public void displayPrompt(String prompt, MapTextFormat textFormat) {
        this.displayPrompt(prompt, textFormat, -16711936, null);
    }

    public void displayPrompt(String prompt, MapTextFormat textFormat, ClickCallback callback) {
        this.displayPrompt(prompt, textFormat, -16711936, callback);
    }

    public void displayPrompt(String prompt, MapTextFormat textFormat, int color, ClickCallback callback) {
        this.setTextFormat(textFormat);
        this.color = color;
        this.displayPromptAtTop(prompt, true, callback);
        if (this._widget.getIcon() != this.icon_lit) {
            this._text.setVisible(true);
            this._widget.setIcon(this.icon_lit);
        }

    }

    public void displayPromptForceShow(String prompt) {
        this.setTextFormat(this.DEFAULT_FORMAT);
        this.color = -16711936;
        this.displayPromptAtTop(prompt, false, null);
        if (this._widget.getIcon() != this.icon_lit) {
            this._text.setVisible(true);
            this._widget.setIcon(this.icon_lit);
        }

    }

    private static String wrap(String prompt, MapTextFormat fmt, float maxWidth) {
        float width = (float)fmt.measureTextWidth(prompt);
        if (width <= maxWidth) {
            return prompt;
        } else {
            if (prompt.contains("\n")) {
                prompt = prompt.replace("\n", " ");
            }

            prompt = prompt.trim() + " ";
            width = 0.0F;
            int lastWrap = 0;
            int lastSpace = 0;
            int lastSentence = 0;
            float sentenceWidth = 0.0F;
            char lastChar = 0;
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < prompt.length(); ++i) {
                char c = prompt.charAt(i);
                if (c == ' ') {
                    float wordWidth = (float)fmt.measureTextWidth(prompt.substring(lastSpace, i));
                    width += wordWidth;
                    if (width > maxWidth) {
                        if (lastSentence > lastWrap && lastSentence < lastSpace && sentenceWidth > maxWidth / 4.0F) {
                            lastSpace = lastSentence;
                            wordWidth = width - sentenceWidth;
                        }

                        String line = prompt.substring(lastWrap, lastSpace);
                        sb.append(line);
                        sb.append("\n");
                        if (i == prompt.length() - 1) {
                            sb.append(prompt, lastSpace + 1, i);
                        }

                        lastWrap = lastSpace + 1;
                        width = wordWidth;
                    } else if (i == prompt.length() - 1) {
                        sb.append(prompt, lastWrap, i);
                    }

                    lastSpace = i;
                    if (lastChar == '.') {
                        sentenceWidth = width;
                        lastSentence = i;
                    }
                }

                lastChar = c;
            }

            return sb.toString().trim();
        }
    }

    private synchronized void displayPromptAtTop(String prompt, boolean blink, ClickCallback callback) {
        this.clickCallback = callback;
        this._prompt = prompt;
        this._mapView.post(new Runnable() {
            public void run() {
            }
        });
        Icon ico = this.getIcon();
        boolean showText = ico == this.icon_lit;
        this._widget.setIcon(ico);
        this._widget.setVisible(true);
        prompt = wrap(prompt, this.textFormat, this._text.getParent().getWidth() - 16.0F);
        if (!this.displaying || !FileSystemUtils.isEquals(prompt, this._text.getText())) {
            this._text.setTextFormat(this.textFormat);
            this._text.setColor(this.color);
            this._text.setText(prompt);
            this._text.setVisible(showText);
            if (MetricsApi.shouldRecordMetric()) {
                Bundle b = new Bundle();
                b.putString("prompt", prompt);
                MetricsApi.record("hint", b);
            }

            this.displaying = true;
            if (blink && !showText) {
                Thread t = new Thread(this, "TextContainerCustom-Blink");
                t.start();
            }
        }

    }

    public void run() {
        for(int i = 0; i < 3 && this.displaying; ++i) {
            try {
                Thread.sleep(175L);
            } catch (InterruptedException var3) {
            }

            this._widget.setIcon(this._widget.getIcon() != this.icon_blank ? this.icon_blank : this.getIcon());
        }

        this._widget.setIcon(this.getIcon());
    }

    private Icon getIcon() {
        return this._prefs.getBoolean("textContainer.textShowing", true) ? this.icon_lit : this.icon_unlit;
    }

    public synchronized void closePrompt(String text) {
        if (text == null || FileSystemUtils.isEquals(text, this._text.getText())) {
            this.displaying = false;
            this._text.setVisible(false);
            this._widget.setVisible(false);
        }

    }

    public void closePrompt() {
        this.closePrompt((String)null);
    }
}

