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
 * Event message for 'progressStart' event type. The event signals that a long running operation is
 * about to start and provides additional information for the client to set up a corresponding
 * progress and cancellation UI. The client is free to delay the showing of the UI in order to
 * reduce flicker. This event should only be sent if the client has passed the value true for the
 * 'supportsProgressReporting' capability of the 'initialize' request.
 */
public class ProgressStartEvent extends Event {

    ProgressStartEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public ProgressStartEvent setBody(EventBody body) {
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
        ProgressStartEvent other = (ProgressStartEvent) obj;
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
        hash = 67 * hash + Objects.hashCode(this.getEvent());
        hash = 67 * hash + Objects.hashCode(this.getBody());
        hash = 67 * hash + Objects.hashCode(this.getType());
        hash = 67 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ProgressStartEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "progressStart");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new ProgressStartEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * An ID that must be used in subsequent 'progressUpdate' and 'progressEnd' events to make
         * them refer to the same progress reporting. IDs must be unique within a debug session.
         */
        public String getProgressId() {
            return jsonData.getString("progressId");
        }

        public EventBody setProgressId(String progressId) {
            jsonData.put("progressId", progressId);
            return this;
        }

        /**
         * Mandatory (short) title of the progress reporting. Shown in the UI to describe the long
         * running operation.
         */
        public String getTitle() {
            return jsonData.getString("title");
        }

        public EventBody setTitle(String title) {
            jsonData.put("title", title);
            return this;
        }

        /**
         * The request ID that this progress report is related to. If specified a debug adapter is
         * expected to emit progress events for the long running request until the request has been
         * either completed or cancelled. If the request ID is omitted, the progress report is
         * assumed to be related to some general activity of the debug adapter.
         */
        public Integer getRequestId() {
            return jsonData.has("requestId") ? jsonData.getInt("requestId") : null;
        }

        public EventBody setRequestId(Integer requestId) {
            jsonData.putOpt("requestId", requestId);
            return this;
        }

        /**
         * If true, the request that reports progress may be canceled with a 'cancel' request. So
         * this property basically controls whether the client should use UX that supports
         * cancellation. Clients that don't support cancellation are allowed to ignore the setting.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getCancellable() {
            return jsonData.has("cancellable") ? jsonData.getBoolean("cancellable") : null;
        }

        public EventBody setCancellable(Boolean cancellable) {
            jsonData.putOpt("cancellable", cancellable);
            return this;
        }

        /**
         * Optional, more detailed progress message.
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
            if (!Objects.equals(this.getTitle(), other.getTitle())) {
                return false;
            }
            if (!Objects.equals(this.getRequestId(), other.getRequestId())) {
                return false;
            }
            if (!Objects.equals(this.getCancellable(), other.getCancellable())) {
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
            hash = 89 * hash + Objects.hashCode(this.getProgressId());
            hash = 89 * hash + Objects.hashCode(this.getTitle());
            if (this.getRequestId() != null) {
                hash = 89 * hash + Integer.hashCode(this.getRequestId());
            }
            if (this.getCancellable() != null) {
                hash = 89 * hash + Boolean.hashCode(this.getCancellable());
            }
            if (this.getMessage() != null) {
                hash = 89 * hash + Objects.hashCode(this.getMessage());
            }
            if (this.getPercentage() != null) {
                hash = 89 * hash + Integer.hashCode(this.getPercentage());
            }
            return hash;
        }

        public static EventBody create(String progressId, String title) {
            final JSONObject json = new JSONObject();
            json.put("progressId", progressId);
            json.put("title", title);
            return new EventBody(json);
        }
    }
}
