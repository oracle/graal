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
 * Event message for 'process' event type. The event indicates that the debugger has begun debugging
 * a new process. Either one that it has launched, or one that it has attached to.
 */
public class ProcessEvent extends Event {

    ProcessEvent(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public EventBody getBody() {
        return new EventBody(jsonData.getJSONObject("body"));
    }

    public ProcessEvent setBody(EventBody body) {
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
        ProcessEvent other = (ProcessEvent) obj;
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
        hash = 83 * hash + Objects.hashCode(this.getEvent());
        hash = 83 * hash + Objects.hashCode(this.getBody());
        hash = 83 * hash + Objects.hashCode(this.getType());
        hash = 83 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ProcessEvent create(EventBody body, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("event", "process");
        json.put("body", body.jsonData);
        json.put("type", "event");
        json.put("seq", seq);
        return new ProcessEvent(json);
    }

    public static class EventBody extends JSONBase {

        EventBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The logical name of the process. This is usually the full path to process's executable
         * file. Example: /home/example/myproj/program.js.
         */
        public String getName() {
            return jsonData.getString("name");
        }

        public EventBody setName(String name) {
            jsonData.put("name", name);
            return this;
        }

        /**
         * The system process id of the debugged process. This property will be missing for
         * non-system processes.
         */
        public Integer getSystemProcessId() {
            return jsonData.has("systemProcessId") ? jsonData.getInt("systemProcessId") : null;
        }

        public EventBody setSystemProcessId(Integer systemProcessId) {
            jsonData.putOpt("systemProcessId", systemProcessId);
            return this;
        }

        /**
         * If true, the process is running on the same computer as the debug adapter.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getIsLocalProcess() {
            return jsonData.has("isLocalProcess") ? jsonData.getBoolean("isLocalProcess") : null;
        }

        public EventBody setIsLocalProcess(Boolean isLocalProcess) {
            jsonData.putOpt("isLocalProcess", isLocalProcess);
            return this;
        }

        /**
         * Describes how the debug engine started debugging this process. 'launch': Process was
         * launched under the debugger. 'attach': Debugger attached to an existing process.
         * 'attachForSuspendedLaunch': A project launcher component has launched a new process in a
         * suspended state and then asked the debugger to attach.
         */
        public String getStartMethod() {
            return jsonData.optString("startMethod", null);
        }

        public EventBody setStartMethod(String startMethod) {
            jsonData.putOpt("startMethod", startMethod);
            return this;
        }

        /**
         * The size of a pointer or address for this process, in bits. This value may be used by
         * clients when formatting addresses for display.
         */
        public Integer getPointerSize() {
            return jsonData.has("pointerSize") ? jsonData.getInt("pointerSize") : null;
        }

        public EventBody setPointerSize(Integer pointerSize) {
            jsonData.putOpt("pointerSize", pointerSize);
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
            if (!Objects.equals(this.getName(), other.getName())) {
                return false;
            }
            if (!Objects.equals(this.getSystemProcessId(), other.getSystemProcessId())) {
                return false;
            }
            if (!Objects.equals(this.getIsLocalProcess(), other.getIsLocalProcess())) {
                return false;
            }
            if (!Objects.equals(this.getStartMethod(), other.getStartMethod())) {
                return false;
            }
            if (!Objects.equals(this.getPointerSize(), other.getPointerSize())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + Objects.hashCode(this.getName());
            if (this.getSystemProcessId() != null) {
                hash = 89 * hash + Integer.hashCode(this.getSystemProcessId());
            }
            if (this.getIsLocalProcess() != null) {
                hash = 89 * hash + Boolean.hashCode(this.getIsLocalProcess());
            }
            if (this.getStartMethod() != null) {
                hash = 89 * hash + Objects.hashCode(this.getStartMethod());
            }
            if (this.getPointerSize() != null) {
                hash = 89 * hash + Integer.hashCode(this.getPointerSize());
            }
            return hash;
        }

        public static EventBody create(String name) {
            final JSONObject json = new JSONObject();
            json.put("name", name);
            return new EventBody(json);
        }
    }
}
