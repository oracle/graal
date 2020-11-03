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
 * Response to 'stackTrace' request.
 */
public class StackTraceResponse extends Response {

    StackTraceResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public StackTraceResponse setBody(ResponseBody body) {
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
        StackTraceResponse other = (StackTraceResponse) obj;
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
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.getBody());
        hash = 89 * hash + Objects.hashCode(this.getType());
        hash = 89 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 89 * hash + Boolean.hashCode(this.isSuccess());
        hash = 89 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 89 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 89 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static StackTraceResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new StackTraceResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The frames of the stackframe. If the array has length zero, there are no stackframes
         * available. This means that there is no location information available.
         */
        public List<StackFrame> getStackFrames() {
            final JSONArray json = jsonData.getJSONArray("stackFrames");
            final List<StackFrame> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(new StackFrame(json.getJSONObject(i)));
            }
            return Collections.unmodifiableList(list);
        }

        public ResponseBody setStackFrames(List<StackFrame> stackFrames) {
            final JSONArray json = new JSONArray();
            for (StackFrame stackFrame : stackFrames) {
                json.put(stackFrame.jsonData);
            }
            jsonData.put("stackFrames", json);
            return this;
        }

        /**
         * The total number of frames available.
         */
        public Integer getTotalFrames() {
            return jsonData.has("totalFrames") ? jsonData.getInt("totalFrames") : null;
        }

        public ResponseBody setTotalFrames(Integer totalFrames) {
            jsonData.putOpt("totalFrames", totalFrames);
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
            if (!Objects.equals(this.getStackFrames(), other.getStackFrames())) {
                return false;
            }
            if (!Objects.equals(this.getTotalFrames(), other.getTotalFrames())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 13 * hash + Objects.hashCode(this.getStackFrames());
            if (this.getTotalFrames() != null) {
                hash = 13 * hash + Integer.hashCode(this.getTotalFrames());
            }
            return hash;
        }

        public static ResponseBody create(List<StackFrame> stackFrames) {
            final JSONObject json = new JSONObject();
            JSONArray stackFramesJsonArr = new JSONArray();
            for (StackFrame stackFrame : stackFrames) {
                stackFramesJsonArr.put(stackFrame.jsonData);
            }
            json.put("stackFrames", stackFramesJsonArr);
            return new ResponseBody(json);
        }
    }
}
