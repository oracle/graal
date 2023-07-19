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
 * Event message for 'continued' event type. The event indicates that the execution of the debuggee
 * has continued. Please note: a debug adapter is not expected to send this event in response to a
 * request that implies that execution continues, e.g. 'launch' or 'continue'. It is only necessary
 * to send a 'continued' event if there was no previous request that implied this.
 */
public class ContinuedEvent extends Event {

    ContinuedEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public ContinuedEvent setBody(EventBody body) {
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
        ContinuedEvent other = (ContinuedEvent) obj;
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
        int hash = 2;
        hash = 29 * hash + Objects.hashCode(this.getEvent());
        hash = 29 * hash + Objects.hashCode(this.getBody());
        hash = 29 * hash + Objects.hashCode(this.getType());
        hash = 29 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ContinuedEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "continued");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new ContinuedEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The thread which was continued.
         */
        public int getThreadId() {
            return jsonData.getInt("threadId");
        }

        public EventBody setThreadId(int threadId) {
            jsonData.put("threadId", threadId);
            return this;
        }

        /**
         * If 'allThreadsContinued' is true, a debug adapter can announce that all threads have
         * continued.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getAllThreadsContinued() {
            return jsonData.has("allThreadsContinued") ? jsonData.getBoolean("allThreadsContinued") : null;
        }

        public EventBody setAllThreadsContinued(Boolean allThreadsContinued) {
            jsonData.putOpt("allThreadsContinued", allThreadsContinued);
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
            if (this.getThreadId() != other.getThreadId()) {
                return false;
            }
            if (!Objects.equals(this.getAllThreadsContinued(), other.getAllThreadsContinued())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 29 * hash + Integer.hashCode(this.getThreadId());
            if (this.getAllThreadsContinued() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getAllThreadsContinued());
            }
            return hash;
        }

        public static EventBody create(Integer threadId) {
            final JSONObject json = new JSONObject();
            json.put("threadId", threadId);
            return new EventBody(json);
        }
    }
}
