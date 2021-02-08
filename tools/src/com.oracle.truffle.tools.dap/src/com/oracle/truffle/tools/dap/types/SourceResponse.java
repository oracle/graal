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
 * Response to 'source' request.
 */
public class SourceResponse extends Response {

    SourceResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return new ResponseBody(jsonData.getJSONObject("body"));
    }

    public SourceResponse setBody(ResponseBody body) {
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
        SourceResponse other = (SourceResponse) obj;
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
        hash = 43 * hash + Objects.hashCode(this.getBody());
        hash = 43 * hash + Objects.hashCode(this.getType());
        hash = 43 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 43 * hash + Boolean.hashCode(this.isSuccess());
        hash = 43 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 43 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 43 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static SourceResponse create(ResponseBody body, Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("body", body.jsonData);
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new SourceResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * Content of the source reference.
         */
        public String getContent() {
            return jsonData.getString("content");
        }

        public ResponseBody setContent(String content) {
            jsonData.put("content", content);
            return this;
        }

        /**
         * Optional content type (mime type) of the source.
         */
        public String getMimeType() {
            return jsonData.optString("mimeType", null);
        }

        public ResponseBody setMimeType(String mimeType) {
            jsonData.putOpt("mimeType", mimeType);
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
            if (!Objects.equals(this.getContent(), other.getContent())) {
                return false;
            }
            if (!Objects.equals(this.getMimeType(), other.getMimeType())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 11 * hash + Objects.hashCode(this.getContent());
            if (this.getMimeType() != null) {
                hash = 11 * hash + Objects.hashCode(this.getMimeType());
            }
            return hash;
        }

        public static ResponseBody create(String content) {
            final JSONObject json = new JSONObject();
            json.put("content", content);
            return new ResponseBody(json);
        }
    }
}
