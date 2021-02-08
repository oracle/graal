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
 * Event message for 'loadedSource' event type. The event indicates that some source has been added,
 * changed, or removed from the set of all loaded sources.
 */
public class LoadedSourceEvent extends Event {

    LoadedSourceEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public LoadedSourceEvent setBody(EventBody body) {
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
        LoadedSourceEvent other = (LoadedSourceEvent) obj;
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
        hash = 23 * hash + Objects.hashCode(this.getEvent());
        hash = 23 * hash + Objects.hashCode(this.getBody());
        hash = 23 * hash + Objects.hashCode(this.getType());
        hash = 23 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static LoadedSourceEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "loadedSource");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new LoadedSourceEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The reason for the event.
         */
        public String getReason() {
            return jsonData.getString("reason");
        }

        public EventBody setReason(String reason) {
            jsonData.put("reason", reason);
            return this;
        }

        /**
         * The new, changed, or removed source.
         */
        public Source getSource() {
            return new Source(jsonData.getJSONObject("source"));
        }

        public EventBody setSource(Source source) {
            jsonData.put("source", source.jsonData);
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
            if (!Objects.equals(this.getSource(), other.getSource())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 2;
            hash = 97 * hash + Objects.hashCode(this.getReason());
            hash = 97 * hash + Objects.hashCode(this.getSource());
            return hash;
        }

        public static EventBody create(String reason, Source source) {
            final JSONObject json = new JSONObject();
            json.put("reason", reason);
            json.put("source", source.jsonData);
            return new EventBody(json);
        }
    }
}
