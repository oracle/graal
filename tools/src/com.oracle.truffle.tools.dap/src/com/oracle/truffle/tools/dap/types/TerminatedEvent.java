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
 * Event message for 'terminated' event type. The event indicates that debugging of the debuggee has
 * terminated. This does **not** mean that the debuggee itself has exited.
 */
public class TerminatedEvent extends Event {

    TerminatedEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return jsonData.has("body") ? new EventBody(jsonData.optJSONObject("body")) : null;
    }

    public TerminatedEvent setBody(EventBody body) {
        jsonData.putOpt("body", body != null ? body.jsonData : null);
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
        TerminatedEvent other = (TerminatedEvent) obj;
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
        hash = 17 * hash + Objects.hashCode(this.getEvent());
        if (this.getBody() != null) {
            hash = 17 * hash + Objects.hashCode(this.getBody());
        }
        hash = 17 * hash + Objects.hashCode(this.getType());
        hash = 17 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static TerminatedEvent create(Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "terminated");
        json.put("type", "event");
        json.put("seq", seq);
        return new TerminatedEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * A debug adapter may set 'restart' to true (or to an arbitrary object) to request that the
         * front end restarts the session. The value is not interpreted by the client and passed
         * unmodified as an attribute '__restart' to the 'launch' and 'attach' requests.
         */
        public Object getRestart() {
            return jsonData.opt("restart");
        }

        public EventBody setRestart(Object restart) {
            jsonData.putOpt("restart", restart);
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
            if (!Objects.equals(this.getRestart(), other.getRestart())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            if (this.getRestart() != null) {
                hash = 17 * hash + Objects.hashCode(this.getRestart());
            }
            return hash;
        }

        public static EventBody create() {
            final JSONObject json = new JSONObject();
            return new EventBody(json);
        }
    }
}
