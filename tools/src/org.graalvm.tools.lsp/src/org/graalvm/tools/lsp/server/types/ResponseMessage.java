/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * A response message.
 */
public class ResponseMessage extends Message {

    ResponseMessage(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The request id.
     */
    public Object getId() {
        return jsonData.get("id");
    }

    public ResponseMessage setId(Object id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * The result of a request. This member is REQUIRED on success. This member MUST NOT exist if
     * there was an error invoking the method.
     */
    public Object getResult() {
        return jsonData.opt("result");
    }

    public ResponseMessage setResult(Object result) {
        jsonData.putOpt("result", result);
        return this;
    }

    /**
     * The error object in case a request fails.
     */
    public ResponseErrorLiteral getError() {
        return jsonData.has("error") ? new ResponseErrorLiteral(jsonData.optJSONObject("error")) : null;
    }

    public ResponseMessage setError(ResponseErrorLiteral error) {
        jsonData.putOpt("error", error != null ? error.jsonData : null);
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
        ResponseMessage other = (ResponseMessage) obj;
        if (!Objects.equals(this.getId(), other.getId())) {
            return false;
        }
        if (!Objects.equals(this.getResult(), other.getResult())) {
            return false;
        }
        if (!Objects.equals(this.getError(), other.getError())) {
            return false;
        }
        if (!Objects.equals(this.getJsonrpc(), other.getJsonrpc())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 11 * hash + Objects.hashCode(this.getId());
        if (this.getResult() != null) {
            hash = 11 * hash + Objects.hashCode(this.getResult());
        }
        if (this.getError() != null) {
            hash = 11 * hash + Objects.hashCode(this.getError());
        }
        hash = 11 * hash + Objects.hashCode(this.getJsonrpc());
        return hash;
    }

    public static ResponseMessage create(Object id, String jsonrpc) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("jsonrpc", jsonrpc);
        return new ResponseMessage(json);
    }
}
