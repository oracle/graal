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

public class ResponseErrorLiteral extends JSONBase {

    ResponseErrorLiteral(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * A number indicating the error type that occured.
     */
    public int getCode() {
        return jsonData.getInt("code");
    }

    public ResponseErrorLiteral setCode(int code) {
        jsonData.put("code", code);
        return this;
    }

    /**
     * A string providing a short decription of the error.
     */
    public String getMessage() {
        return jsonData.getString("message");
    }

    public ResponseErrorLiteral setMessage(String message) {
        jsonData.put("message", message);
        return this;
    }

    /**
     * A Primitive or Structured value that contains additional information about the error. Can be
     * omitted.
     */
    public Object getData() {
        return jsonData.opt("data");
    }

    public ResponseErrorLiteral setData(Object data) {
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
        ResponseErrorLiteral other = (ResponseErrorLiteral) obj;
        if (this.getCode() != other.getCode()) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (!Objects.equals(this.getData(), other.getData())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 83 * hash + Integer.hashCode(this.getCode());
        hash = 83 * hash + Objects.hashCode(this.getMessage());
        if (this.getData() != null) {
            hash = 83 * hash + Objects.hashCode(this.getData());
        }
        return hash;
    }

    public static ResponseErrorLiteral create(Integer code, String message) {
        final JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("message", message);
        return new ResponseErrorLiteral(json);
    }
}
