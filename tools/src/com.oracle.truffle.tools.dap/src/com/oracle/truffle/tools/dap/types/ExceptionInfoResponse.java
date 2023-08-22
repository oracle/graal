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
 * Response to 'exceptionInfo' request.
 */
public class ExceptionInfoResponse extends Response {

    ExceptionInfoResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public ExceptionInfoResponse setBody(ResponseBody body) {
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
        ExceptionInfoResponse other = (ExceptionInfoResponse) obj;
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
        hash = 59 * hash + Objects.hashCode(this.getBody());
        hash = 59 * hash + Objects.hashCode(this.getType());
        hash = 59 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 59 * hash + Boolean.hashCode(this.isSuccess());
        hash = 59 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 59 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 59 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ExceptionInfoResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new ExceptionInfoResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * ID of the exception that was thrown.
         */
        public String getExceptionId() {
            return jsonData.getString("exceptionId");
        }

        public ResponseBody setExceptionId(String exceptionId) {
            jsonData.put("exceptionId", exceptionId);
            return this;
        }

        /**
         * Descriptive text for the exception provided by the debug adapter.
         */
        public String getDescription() {
            return jsonData.optString("description", null);
        }

        public ResponseBody setDescription(String description) {
            jsonData.putOpt("description", description);
            return this;
        }

        /**
         * Mode that caused the exception notification to be raised.
         */
        public String getBreakMode() {
            return jsonData.getString("breakMode");
        }

        public ResponseBody setBreakMode(String breakMode) {
            jsonData.put("breakMode", breakMode);
            return this;
        }

        /**
         * Detailed information about the exception.
         */
        public ExceptionDetails getDetails() {
            return jsonData.has("details") ? new ExceptionDetails(jsonData.optJSONObject("details")) : null;
        }

        public ResponseBody setDetails(ExceptionDetails details) {
            jsonData.putOpt("details", details != null ? details.jsonData : null);
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
            if (!Objects.equals(this.getExceptionId(), other.getExceptionId())) {
                return false;
            }
            if (!Objects.equals(this.getDescription(), other.getDescription())) {
                return false;
            }
            if (!Objects.equals(this.getBreakMode(), other.getBreakMode())) {
                return false;
            }
            if (!Objects.equals(this.getDetails(), other.getDetails())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 47 * hash + Objects.hashCode(this.getExceptionId());
            if (this.getDescription() != null) {
                hash = 47 * hash + Objects.hashCode(this.getDescription());
            }
            hash = 47 * hash + Objects.hashCode(this.getBreakMode());
            if (this.getDetails() != null) {
                hash = 47 * hash + Objects.hashCode(this.getDetails());
            }
            return hash;
        }

        public static ResponseBody create(String exceptionId, String breakMode) {
            final JSONObject json = new JSONObject();
            json.put("exceptionId", exceptionId);
            json.put("breakMode", breakMode);
            return new ResponseBody(json);
        }
    }
}
