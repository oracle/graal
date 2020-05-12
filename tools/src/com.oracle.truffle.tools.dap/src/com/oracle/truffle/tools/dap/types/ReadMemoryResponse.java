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
 * Response to 'readMemory' request.
 */
public class ReadMemoryResponse extends Response {

    ReadMemoryResponse(JSONObject jsonData) {
        super(jsonData);
    }

    @Override
    public ResponseBody getBody() {
        return jsonData.has("body") ? new ResponseBody(jsonData.optJSONObject("body")) : null;
    }

    public ReadMemoryResponse setBody(ResponseBody body) {
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
        ReadMemoryResponse other = (ReadMemoryResponse) obj;
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
        if (this.getBody() != null) {
            hash = 53 * hash + Objects.hashCode(this.getBody());
        }
        hash = 53 * hash + Objects.hashCode(this.getType());
        hash = 53 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 53 * hash + Boolean.hashCode(this.isSuccess());
        hash = 53 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 53 * hash + Objects.hashCode(this.getMessage());
        }
        hash = 53 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static ReadMemoryResponse create(Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new ReadMemoryResponse(json);
    }

    public static class ResponseBody extends JSONBase {

        ResponseBody(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The address of the first byte of data returned. Treated as a hex value if prefixed with
         * '0x', or as a decimal value otherwise.
         */
        public String getAddress() {
            return jsonData.getString("address");
        }

        public ResponseBody setAddress(String address) {
            jsonData.put("address", address);
            return this;
        }

        /**
         * The number of unreadable bytes encountered after the last successfully read byte. This
         * can be used to determine the number of bytes that must be skipped before a subsequent
         * 'readMemory' request will succeed.
         */
        public Integer getUnreadableBytes() {
            return jsonData.has("unreadableBytes") ? jsonData.getInt("unreadableBytes") : null;
        }

        public ResponseBody setUnreadableBytes(Integer unreadableBytes) {
            jsonData.putOpt("unreadableBytes", unreadableBytes);
            return this;
        }

        /**
         * The bytes read from memory, encoded using base64.
         */
        public String getData() {
            return jsonData.optString("data", null);
        }

        public ResponseBody setData(String data) {
            jsonData.putOpt("data", data);
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
            if (!Objects.equals(this.getAddress(), other.getAddress())) {
                return false;
            }
            if (!Objects.equals(this.getUnreadableBytes(), other.getUnreadableBytes())) {
                return false;
            }
            if (!Objects.equals(this.getData(), other.getData())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.getAddress());
            if (this.getUnreadableBytes() != null) {
                hash = 37 * hash + Integer.hashCode(this.getUnreadableBytes());
            }
            if (this.getData() != null) {
                hash = 37 * hash + Objects.hashCode(this.getData());
            }
            return hash;
        }

        public static ResponseBody create(String address) {
            final JSONObject json = new JSONObject();
            json.put("address", address);
            return new ResponseBody(json);
        }
    }
}
