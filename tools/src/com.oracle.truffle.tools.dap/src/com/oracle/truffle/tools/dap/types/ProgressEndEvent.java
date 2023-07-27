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
 * Event message for 'progressEnd' event type. The event signals the end of the progress reporting
 * with an optional final message. This event should only be sent if the client has passed the value
 * true for the 'supportsProgressReporting' capability of the 'initialize' request.
 */
public class ProgressEndEvent extends Event {

    ProgressEndEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public ProgressEndEvent setBody(EventBody body) {
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
        ProgressEndEvent other = (ProgressEndEvent) obj;
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
        int hash = 3;
        hash = 89 * hash + Objects.hashCode(this.getEvent());
        hash = 89 * hash + Objects.hashCode(this.getBody());
        hash = 89 * hash + Objects.hashCode(this.getType());
        hash = 89 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ProgressEndEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "progressEnd");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new ProgressEndEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The ID that was introduced in the initial 'ProgressStartEvent'.
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
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 59 * hash + Objects.hashCode(this.getProgressId());
            if (this.getMessage() != null) {
                hash = 59 * hash + Objects.hashCode(this.getMessage());
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
