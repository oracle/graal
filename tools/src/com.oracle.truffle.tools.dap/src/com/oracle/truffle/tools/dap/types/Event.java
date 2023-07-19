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
 * A debug adapter initiated event.
 */
public class Event extends ProtocolMessage {

    Event(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Type of event.
     */
    public String getEvent() {
        return jsonData.getString("event");
    }

    public Event setEvent(String event) {
        jsonData.put("event", event);
        return this;
    }

    /**
     * Event-specific information.
     */
    public Object getBody() {
        return jsonData.opt("body");
    }

    public Event setBody(Object body) {
        jsonData.putOpt("body", body);
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
        Event other = (Event) obj;
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (!Objects.equals(this.getEvent(), other.getEvent())) {
            return false;
        }
        if (!Objects.equals(this.getBody(), other.getBody())) {
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
        hash = 83 * hash + Objects.hashCode(this.getType());
        hash = 83 * hash + Objects.hashCode(this.getEvent());
        if (this.getBody() != null) {
            hash = 83 * hash + Objects.hashCode(this.getBody());
        }
        hash = 83 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static Event create(String event, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("type", "event");
        json.put("event", event);
        json.put("seq", seq);
        return new Event(json);
    }
}
