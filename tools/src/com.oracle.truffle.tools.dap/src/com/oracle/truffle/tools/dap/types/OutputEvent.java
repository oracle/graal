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

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * Event message for 'output' event type. The event indicates that the target has produced some
 * output.
 */
public class OutputEvent extends Event {

    OutputEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public OutputEvent setBody(EventBody body) {
        jsonData.put("body", body.jsonData);
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
        OutputEvent other = (OutputEvent) obj;
        if (!Objects.equals(this.getEvent(), other.getEvent())) {
            return false;
        }
        if (!Objects.equals(this.getBody(), other.getBody())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + Objects.hashCode(this.getEvent());
        hash = 83 * hash + Objects.hashCode(this.getBody());
        hash = 83 * hash + Objects.hashCode(this.getType());
        hash = 83 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static OutputEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "output");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new OutputEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The output category. If not specified, 'console' is assumed. Values: 'console', 'stdout',
         * 'stderr', 'telemetry', etc.
         */
        public String getCategory() {
            return jsonData.optString("category", null);
        }

        public EventBody setCategory(String category) {
            jsonData.putOpt("category", category);
            return this;
        }

        /**
         * The output to report.
         */
        public String getOutput() {
            return jsonData.getString("output");
        }

        public EventBody setOutput(String output) {
            jsonData.put("output", output);
            return this;
        }

        /**
         * Support for keeping an output log organized by grouping related messages. 'start': Start
         * a new group in expanded mode. Subsequent output events are members of the group and
         * should be shown indented. The 'output' attribute becomes the name of the group and is not
         * indented. 'startCollapsed': Start a new group in collapsed mode. Subsequent output events
         * are members of the group and should be shown indented (as soon as the group is expanded).
         * The 'output' attribute becomes the name of the group and is not indented. 'end': End the
         * current group and decreases the indentation of subsequent output events. A non empty
         * 'output' attribute is shown as the unindented end of the group.
         */
        public String getGroup() {
            return jsonData.optString("group", null);
        }

        public EventBody setGroup(String group) {
            jsonData.putOpt("group", group);
            return this;
        }

        /**
         * If an attribute 'variablesReference' exists and its value is > 0, the output contains
         * objects which can be retrieved by passing 'variablesReference' to the 'variables'
         * request. The value should be less than or equal to 2147483647 (2^31 - 1).
         */
        public Integer getVariablesReference() {
            return jsonData.has("variablesReference") ? jsonData.getInt("variablesReference") : null;
        }

        public EventBody setVariablesReference(Integer variablesReference) {
            jsonData.putOpt("variablesReference", variablesReference);
            return this;
        }

        /**
         * An optional source location where the output was produced.
         */
        public Source getSource() {
            return jsonData.has("source") ? new Source(jsonData.optJSONObject("source")) : null;
        }

        public EventBody setSource(Source source) {
            jsonData.putOpt("source", source != null ? source.jsonData : null);
            return this;
        }

        /**
         * An optional source location line where the output was produced.
         */
        public Integer getLine() {
            return jsonData.has("line") ? jsonData.getInt("line") : null;
        }

        public EventBody setLine(Integer line) {
            jsonData.putOpt("line", line);
            return this;
        }

        /**
         * An optional source location column where the output was produced.
         */
        public Integer getColumn() {
            return jsonData.has("column") ? jsonData.getInt("column") : null;
        }

        public EventBody setColumn(Integer column) {
            jsonData.putOpt("column", column);
            return this;
        }

        /**
         * Optional data to report. For the 'telemetry' category the data will be sent to telemetry,
         * for the other categories the data is shown in JSON format.
         */
        public Object getData() {
            return jsonData.opt("data");
        }

        public EventBody setData(Object data) {
            jsonData.putOpt("data", data);
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
            EventBody other = (EventBody) obj;
            if (!Objects.equals(this.getCategory(), other.getCategory())) {
                return false;
            }
            if (!Objects.equals(this.getOutput(), other.getOutput())) {
                return false;
            }
            if (!Objects.equals(this.getGroup(), other.getGroup())) {
                return false;
            }
            if (!Objects.equals(this.getVariablesReference(), other.getVariablesReference())) {
                return false;
            }
            if (!Objects.equals(this.getSource(), other.getSource())) {
                return false;
            }
            if (!Objects.equals(this.getLine(), other.getLine())) {
                return false;
            }
            if (!Objects.equals(this.getColumn(), other.getColumn())) {
                return false;
            }
            if (!Objects.equals(this.getData(), other.getData())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 2;
            if (this.getCategory() != null) {
                hash = 23 * hash + Objects.hashCode(this.getCategory());
            }
            hash = 23 * hash + Objects.hashCode(this.getOutput());
            if (this.getGroup() != null) {
                hash = 23 * hash + Objects.hashCode(this.getGroup());
            }
            if (this.getVariablesReference() != null) {
                hash = 23 * hash + Integer.hashCode(this.getVariablesReference());
            }
            if (this.getSource() != null) {
                hash = 23 * hash + Objects.hashCode(this.getSource());
            }
            if (this.getLine() != null) {
                hash = 23 * hash + Integer.hashCode(this.getLine());
            }
            if (this.getColumn() != null) {
                hash = 23 * hash + Integer.hashCode(this.getColumn());
            }
            if (this.getData() != null) {
                hash = 23 * hash + Objects.hashCode(this.getData());
            }
            return hash;
        }

        public static EventBody create(String output) {
            final JSONObject json = new JSONObject();
            json.put("output", output);
            return new EventBody(json);
        }
    }
}
