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
 * Notification Message.
 */
public class NotificationMessage extends Message {

    NotificationMessage(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The method to be invoked.
     */
    public String getMethod() {
        return jsonData.getString("method");
    }

    public NotificationMessage setMethod(String method) {
        jsonData.put("method", method);
        return this;
    }

    /**
     * The notification's params.
     */
    public Object getParams() {
        return jsonData.opt("params");
    }

    public NotificationMessage setParams(Object params) {
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
        NotificationMessage other = (NotificationMessage) obj;
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
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.getMethod());
        if (this.getParams() != null) {
            hash = 29 * hash + Objects.hashCode(this.getParams());
        }
        hash = 29 * hash + Objects.hashCode(this.getJsonrpc());
        return hash;
    }

    public static NotificationMessage create(String method, String jsonrpc) {
        final JSONObject json = new JSONObject();
        json.put("method", method);
        json.put("jsonrpc", jsonrpc);
        return new NotificationMessage(json);
    }
}
