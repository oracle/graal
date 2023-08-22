/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.dap.types;

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A structured message object. Used to return errors from requests.
 */
public class Message extends JSONBase {

    Message(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Unique identifier for the message.
     */
    public int getId() {
        return jsonData.getInt("id");
    }

    public Message setId(int id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * A format string for the message. Embedded variables have the form '{name}'. If variable name
     * starts with an underscore character, the variable does not contain user data (PII) and can be
     * safely used for telemetry purposes.
     */
    public String getFormat() {
        return jsonData.getString("format");
    }

    public Message setFormat(String format) {
        jsonData.put("format", format);
        return this;
    }

    /**
     * An object used as a dictionary for looking up the variables in the format string.
     */
    public Map<String, String> getVariables() {
        final JSONObject json = jsonData.optJSONObject("variables");
        if (json == null) {
            return null;
        }
        final Map<String, String> map = new HashMap<>(json.length());
        for (String key : json.keySet()) {
            map.put(key, json.getString(key));
        }
        return map;
    }

    public Message setVariables(Map<String, String> variables) {
        if (variables != null) {
            final JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            jsonData.put("variables", json);
        }
        return this;
    }

    /**
     * If true send to telemetry.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSendTelemetry() {
        return jsonData.has("sendTelemetry") ? jsonData.getBoolean("sendTelemetry") : null;
    }

    public Message setSendTelemetry(Boolean sendTelemetry) {
        jsonData.putOpt("sendTelemetry", sendTelemetry);
        return this;
    }

    /**
     * If true show user.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getShowUser() {
        return jsonData.has("showUser") ? jsonData.getBoolean("showUser") : null;
    }

    public Message setShowUser(Boolean showUser) {
        jsonData.putOpt("showUser", showUser);
        return this;
    }

    /**
     * An optional url where additional information about this message can be found.
     */
    public String getUrl() {
        return jsonData.optString("url", null);
    }

    public Message setUrl(String url) {
        jsonData.putOpt("url", url);
        return this;
    }

    /**
     * An optional label that is presented to the user as the UI for opening the url.
     */
    public String getUrlLabel() {
        return jsonData.optString("urlLabel", null);
    }

    public Message setUrlLabel(String urlLabel) {
        jsonData.putOpt("urlLabel", urlLabel);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        Message other = (Message) obj;
        if (this.getId() != other.getId()) {
            return false;
        }
        if (!Objects.equals(this.getFormat(), other.getFormat())) {
            return false;
        }
        if (!Objects.equals(this.getVariables(), other.getVariables())) {
            return false;
        }
        if (!Objects.equals(this.getSendTelemetry(), other.getSendTelemetry())) {
            return false;
        }
        if (!Objects.equals(this.getShowUser(), other.getShowUser())) {
            return false;
        }
        if (!Objects.equals(this.getUrl(), other.getUrl())) {
            return false;
        }
        if (!Objects.equals(this.getUrlLabel(), other.getUrlLabel())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Integer.hashCode(this.getId());
        hash = 97 * hash + Objects.hashCode(this.getFormat());
        if (this.getVariables() != null) {
            hash = 97 * hash + Objects.hashCode(this.getVariables());
        }
        if (this.getSendTelemetry() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getSendTelemetry());
        }
        if (this.getShowUser() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getShowUser());
        }
        if (this.getUrl() != null) {
            hash = 97 * hash + Objects.hashCode(this.getUrl());
        }
        if (this.getUrlLabel() != null) {
            hash = 97 * hash + Objects.hashCode(this.getUrlLabel());
        }
        return hash;
    }

    public static Message create(Integer id, String format) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("format", format);
        return new Message(json);
    }
}
