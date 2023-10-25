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

import java.util.Objects;

/**
 * Event message for 'progressUpdate' event type. The event signals that the progress reporting
 * needs to updated with a new message and/or percentage. The client does not have to update the UI
 * immediately, but the clients needs to keep track of the message and/or percentage values. This
 * event should only be sent if the client has passed the value true for the
 * 'supportsProgressReporting' capability of the 'initialize' request.
 */
public class ProgressUpdateEvent extends Event {

    ProgressUpdateEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public ProgressUpdateEvent setBody(EventBody body) {
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
        ProgressUpdateEvent other = (ProgressUpdateEvent) obj;
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
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.getEvent());
        hash = 59 * hash + Objects.hashCode(this.getBody());
        hash = 59 * hash + Objects.hashCode(this.getType());
        hash = 59 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ProgressUpdateEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "progressUpdate");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new ProgressUpdateEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The ID that was introduced in the initial 'progressStart' event.
         */
        public String getProgressId() {
            return jsonData.getString("progressId");
        }

        public EventBody setProgressId(String progressId) {
            jsonData.put("progressId", progressId);
            return this;
        }

        /**
         * Optional, more detailed progress message. If omitted, the previous message (if any) is
         * used.
         */
        public String getMessage() {
            return jsonData.optString("message", null);
        }

        public EventBody setMessage(String message) {
            jsonData.putOpt("message", message);
            return this;
        }

        /**
         * Optional progress percentage to display (value range: 0 to 100). If omitted no percentage
         * will be shown.
         */
        public Integer getPercentage() {
            return jsonData.has("percentage") ? jsonData.getInt("percentage") : null;
        }

        public EventBody setPercentage(Integer percentage) {
            jsonData.putOpt("percentage", percentage);
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
            if (!Objects.equals(this.getProgressId(), other.getProgressId())) {
                return false;
            }
            if (!Objects.equals(this.getMessage(), other.getMessage())) {
                return false;
            }
            if (!Objects.equals(this.getPercentage(), other.getPercentage())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.getProgressId());
            if (this.getMessage() != null) {
                hash = 29 * hash + Objects.hashCode(this.getMessage());
            }
            if (this.getPercentage() != null) {
                hash = 29 * hash + Integer.hashCode(this.getPercentage());
            }
            return hash;
        }

        public static EventBody create(String progressId) {
            final JSONObject json = new JSONObject();
            json.put("progressId", progressId);
            return new EventBody(json);
        }
    }
}
