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
 * Event message for 'breakpoint' event type. The event indicates that some information about a
 * breakpoint has changed.
 */
public class BreakpointEvent extends Event {

    BreakpointEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public BreakpointEvent setBody(EventBody body) {
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
        BreakpointEvent other = (BreakpointEvent) obj;
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

    public static BreakpointEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "breakpoint");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new BreakpointEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The reason for the event. Values: 'changed', 'new', 'removed', etc.
         */
        public String getReason() {
            return jsonData.getString("reason");
        }

        public EventBody setReason(String reason) {
            jsonData.put("reason", reason);
            return this;
        }

        /**
         * The 'id' attribute is used to find the target breakpoint and the other attributes are
         * used as the new values.
         */
        public Breakpoint getBreakpoint() {
            return new Breakpoint(jsonData.getJSONObject("breakpoint"));
        }

        public EventBody setBreakpoint(Breakpoint breakpoint) {
            jsonData.put("breakpoint", breakpoint.jsonData);
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
            if (!Objects.equals(this.getReason(), other.getReason())) {
                return false;
            }
            if (!Objects.equals(this.getBreakpoint(), other.getBreakpoint())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 59 * hash + Objects.hashCode(this.getReason());
            hash = 59 * hash + Objects.hashCode(this.getBreakpoint());
            return hash;
        }

        public static EventBody create(String reason, Breakpoint breakpoint) {
            final JSONObject json = new JSONObject();
            json.put("reason", reason);
            json.put("breakpoint", breakpoint.jsonData);
            return new EventBody(json);
        }
    }
}
