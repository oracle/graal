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
 * Request message.
 */
public class RequestMessage extends Message {

    RequestMessage(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The request id.
     */
    public Object getId() {
        return jsonData.get("id");
    }

    public RequestMessage setId(Object id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * The method to be invoked.
     */
    public String getMethod() {
        return jsonData.getString("method");
    }

    public RequestMessage setMethod(String method) {
        jsonData.put("method", method);
        return this;
    }

    /**
     * The method's params.
     */
    public Object getParams() {
        return jsonData.opt("params");
    }

    public RequestMessage setParams(Object params) {
        jsonData.putOpt("params", params);
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
        RequestMessage other = (RequestMessage) obj;
        if (!Objects.equals(this.getId(), other.getId())) {
            return false;
        }
        if (!Objects.equals(this.getMethod(), other.getMethod())) {
            return false;
        }
        if (!Objects.equals(this.getParams(), other.getParams())) {
            return false;
        }
        if (!Objects.equals(this.getJsonrpc(), other.getJsonrpc())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.getId());
        hash = 79 * hash + Objects.hashCode(this.getMethod());
        if (this.getParams() != null) {
            hash = 79 * hash + Objects.hashCode(this.getParams());
        }
        hash = 79 * hash + Objects.hashCode(this.getJsonrpc());
        return hash;
    }

    public static RequestMessage create(Object id, String method, String jsonrpc) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("method", method);
        json.put("jsonrpc", jsonrpc);
        return new RequestMessage(json);
    }
}
