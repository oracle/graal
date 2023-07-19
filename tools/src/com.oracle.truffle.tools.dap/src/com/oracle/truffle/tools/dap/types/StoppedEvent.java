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
 * Event message for 'stopped' event type. The event indicates that the execution of the debuggee
 * has stopped due to some condition. This can be caused by a break point previously set, a stepping
 * action has completed, by executing a debugger statement etc.
 */
public class StoppedEvent extends Event {

    StoppedEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public StoppedEvent setBody(EventBody body) {
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
        StoppedEvent other = (StoppedEvent) obj;
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
        hash = 71 * hash + Objects.hashCode(this.getEvent());
        hash = 71 * hash + Objects.hashCode(this.getBody());
        hash = 71 * hash + Objects.hashCode(this.getType());
        hash = 71 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static StoppedEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "stopped");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new StoppedEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The reason for the event. For backward compatibility this string is shown in the UI if
         * the 'description' attribute is missing (but it must not be translated). Values: 'step',
         * 'breakpoint', 'exception', 'pause', 'entry', 'goto', 'function breakpoint', 'data
         * breakpoint', etc.
         */
        public String getReason() {
            return jsonData.getString("reason");
        }

        public EventBody setReason(String reason) {
            jsonData.put("reason", reason);
            return this;
        }

        /**
         * The full reason for the event, e.g. 'Paused on exception'. This string is shown in the UI
         * as is and must be translated.
         */
        public String getDescription() {
            return jsonData.optString("description", null);
        }

        public EventBody setDescription(String description) {
            jsonData.putOpt("description", description);
            return this;
        }

        /**
         * The thread which was stopped.
         */
        public Integer getThreadId() {
            return jsonData.has("threadId") ? jsonData.getInt("threadId") : null;
        }

        public EventBody setThreadId(Integer threadId) {
            jsonData.putOpt("threadId", threadId);
            return this;
        }

        /**
         * A value of true hints to the frontend that this event should not change the focus.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getPreserveFocusHint() {
            return jsonData.has("preserveFocusHint") ? jsonData.getBoolean("preserveFocusHint") : null;
        }

        public EventBody setPreserveFocusHint(Boolean preserveFocusHint) {
            jsonData.putOpt("preserveFocusHint", preserveFocusHint);
            return this;
        }

        /**
         * Additional information. E.g. if reason is 'exception', text contains the exception name.
         * This string is shown in the UI.
         */
        public String getText() {
            return jsonData.optString("text", null);
        }

        public EventBody setText(String text) {
            jsonData.putOpt("text", text);
            return this;
        }

        /**
         * If 'allThreadsStopped' is true, a debug adapter can announce that all threads have
         * stopped. - The client should use this information to enable that all threads can be
         * expanded to access their stacktraces. - If the attribute is missing or false, only the
         * thread with the given threadId can be expanded.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getAllThreadsStopped() {
            return jsonData.has("allThreadsStopped") ? jsonData.getBoolean("allThreadsStopped") : null;
        }

        public EventBody setAllThreadsStopped(Boolean allThreadsStopped) {
            jsonData.putOpt("allThreadsStopped", allThreadsStopped);
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
            if (!Objects.equals(this.getDescription(), other.getDescription())) {
                return false;
            }
            if (!Objects.equals(this.getThreadId(), other.getThreadId())) {
                return false;
            }
            if (!Objects.equals(this.getPreserveFocusHint(), other.getPreserveFocusHint())) {
                return false;
            }
            if (!Objects.equals(this.getText(), other.getText())) {
                return false;
            }
            if (!Objects.equals(this.getAllThreadsStopped(), other.getAllThreadsStopped())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 11 * hash + Objects.hashCode(this.getReason());
            if (this.getDescription() != null) {
                hash = 11 * hash + Objects.hashCode(this.getDescription());
            }
            if (this.getThreadId() != null) {
                hash = 11 * hash + Integer.hashCode(this.getThreadId());
            }
            if (this.getPreserveFocusHint() != null) {
                hash = 11 * hash + Boolean.hashCode(this.getPreserveFocusHint());
            }
            if (this.getText() != null) {
                hash = 11 * hash + Objects.hashCode(this.getText());
            }
            if (this.getAllThreadsStopped() != null) {
                hash = 11 * hash + Boolean.hashCode(this.getAllThreadsStopped());
            }
            return hash;
        }

        public static EventBody create(String reason) {
            final JSONObject json = new JSONObject();
            json.put("reason", reason);
            return new EventBody(json);
        }
    }
}
