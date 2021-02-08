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
 * Event message for 'capabilities' event type. The event indicates that one or more capabilities
 * have changed. Since the capabilities are dependent on the frontend and its UI, it might not be
 * possible to change that at random times (or too late). Consequently this event has a hint
 * characteristic: a frontend can only be expected to make a 'best effort' in honouring individual
 * capabilities but there are no guarantees. Only changed capabilities need to be included, all
 * other capabilities keep their values.
 */
public class CapabilitiesEvent extends Event {

    CapabilitiesEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public CapabilitiesEvent setBody(EventBody body) {
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
        CapabilitiesEvent other = (CapabilitiesEvent) obj;
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

    public static CapabilitiesEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "capabilities");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new CapabilitiesEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The set of updated capabilities.
         */
        public Capabilities getCapabilities() {
            return new Capabilities(jsonData.getJSONObject("capabilities"));
        }

        public EventBody setCapabilities(Capabilities capabilities) {
            jsonData.put("capabilities", capabilities.jsonData);
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
            if (!Objects.equals(this.getCapabilities(), other.getCapabilities())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.getCapabilities());
            return hash;
        }

        public static EventBody create(Capabilities capabilities) {
            final JSONObject json = new JSONObject();
            json.put("capabilities", capabilities.jsonData);
            return new EventBody(json);
        }
    }
}
