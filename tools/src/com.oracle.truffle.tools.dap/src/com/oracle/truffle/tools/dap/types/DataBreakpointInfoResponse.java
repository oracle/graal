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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Response to 'dataBreakpointInfo' request.
 */
public class DataBreakpointInfoResponse extends Response {

    DataBreakpointInfoResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public DataBreakpointInfoResponse setBody(ResponseBody body) {
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
        DataBreakpointInfoResponse other = (DataBreakpointInfoResponse) obj;
        if (!Objects.equals(this.getBody(), other.getBody())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (this.getRequestSeq() != other.getRequestSeq()) {
            return false;
        }
        if (this.isSuccess() != other.isSuccess()) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
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
        hash = 37 * hash + Objects.hashCode(this.getBody());
        hash = 37 * hash + Objects.hashCode(this.getType());
        hash = 37 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 37 * hash + Boolean.hashCode(this.isSuccess());
        hash = 37 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 37 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 37 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static DataBreakpointInfoResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new DataBreakpointInfoResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * An identifier for the data on which a data breakpoint can be registered with the
         * setDataBreakpoints request or null if no data breakpoint is available.
         */
        public String getDataId() {
            Object obj = jsonData.get("dataId");
            return JSONObject.NULL.equals(obj) ? null : (String) obj;
        }

        public ResponseBody setDataId(String dataId) {
            jsonData.put("dataId", dataId == null ? JSONObject.NULL : dataId);
            return this;
        }

        /**
         * UI string that describes on what data the breakpoint is set on or why a data breakpoint
         * is not available.
         */
        public String getDescription() {
            return jsonData.getString("description");
        }

        public ResponseBody setDescription(String description) {
            jsonData.put("description", description);
            return this;
        }

        /**
         * Optional attribute listing the available access types for a potential data breakpoint. A
         * UI frontend could surface this information.
         */
        public List<String> getAccessTypes() {
            final JSONArray json = jsonData.optJSONArray("accessTypes");
            if (json == null) {
                return null;
            }
            final List<String> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(json.getString(i));
            }
            return Collections.unmodifiableList(list);
        }

        public ResponseBody setAccessTypes(List<String> accessTypes) {
            if (accessTypes != null) {
                final JSONArray json = new JSONArray();
                for (String string : accessTypes) {
                    json.put(string);
                }
                jsonData.put("accessTypes", json);
            }
            return this;
        }

        /**
         * Optional attribute indicating that a potential data breakpoint could be persisted across
         * sessions.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getCanPersist() {
            return jsonData.has("canPersist") ? jsonData.getBoolean("canPersist") : null;
        }

        public ResponseBody setCanPersist(Boolean canPersist) {
            jsonData.putOpt("canPersist", canPersist);
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
            ResponseBody other = (ResponseBody) obj;
            if (!Objects.equals(this.getDataId(), other.getDataId())) {
                return false;
            }
            if (!Objects.equals(this.getDescription(), other.getDescription())) {
                return false;
            }
            if (!Objects.equals(this.getAccessTypes(), other.getAccessTypes())) {
                return false;
            }
            if (!Objects.equals(this.getCanPersist(), other.getCanPersist())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDataId() != null) {
                hash = 29 * hash + Objects.hashCode(this.getDataId());
            }
            hash = 29 * hash + Objects.hashCode(this.getDescription());
            if (this.getAccessTypes() != null) {
                hash = 29 * hash + Objects.hashCode(this.getAccessTypes());
            }
            if (this.getCanPersist() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getCanPersist());
            }
            return hash;
        }

        public static ResponseBody create(String dataId, String description) {
            final JSONObject json = new JSONObject();
            json.put("dataId", dataId == null ? JSONObject.NULL : dataId);
            json.put("description", description);
            return new ResponseBody(json);
        }
    }
}
