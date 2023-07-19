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
 * Response for a request.
 */
public class Response extends ProtocolMessage {

    Response(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Sequence number of the corresponding request.
     */
    public int getRequestSeq() {
        return jsonData.getInt("request_seq");
    }

    public Response setRequestSeq(int requestSeq) {
        jsonData.put("request_seq", requestSeq);
        return this;
    }

    /**
     * Outcome of the request. If true, the request was successful and the 'body' attribute may
     * contain the result of the request. If the value is false, the attribute 'message' contains
     * the error in short form and the 'body' may contain additional information (see
     * 'ErrorResponse.body.error').
     */
    public boolean isSuccess() {
        return jsonData.getBoolean("success");
    }

    public Response setSuccess(boolean success) {
        jsonData.put("success", success);
        return this;
    }

    /**
     * The command requested.
     */
    public String getCommand() {
        return jsonData.getString("command");
    }

    public Response setCommand(String command) {
        jsonData.put("command", command);
        return this;
    }

    /**
     * Contains the raw error in short form if 'success' is false. This raw error might be
     * interpreted by the frontend and is not shown in the UI. Some predefined values exist. Values:
     * 'cancelled': request was cancelled. etc.
     */
    public String getMessage() {
        return jsonData.optString("message", null);
    }

    public Response setMessage(String message) {
        jsonData.putOpt("message", message);
        return this;
    }

    /**
     * Contains request result if success is true and optional error details if success is false.
     */
    public Object getBody() {
        return jsonData.opt("body");
    }

    public Response setBody(Object body) {
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
        Response other = (Response) obj;
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
        hash = 53 * hash + Objects.hashCode(this.getType());
        hash = 53 * hash + Integer.hashCode(this.getRequestSeq());
        hash = 53 * hash + Boolean.hashCode(this.isSuccess());
        hash = 53 * hash + Objects.hashCode(this.getCommand());
        if (this.getMessage() != null) {
            hash = 53 * hash + Objects.hashCode(this.getMessage());
        }
        if (this.getBody() != null) {
            hash = 53 * hash + Objects.hashCode(this.getBody());
        }
        hash = 53 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static Response create(Integer requestSeq, Boolean success, String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("type", "response");
        json.put("request_seq", requestSeq);
        json.put("success", success);
        json.put("command", command);
        json.put("seq", seq);
        return new Response(json);
    }
}
