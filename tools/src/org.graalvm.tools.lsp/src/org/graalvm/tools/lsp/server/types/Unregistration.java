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
 * General parameters to unregister a request or notification.
 */
public class Unregistration extends JSONBase {

    Unregistration(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The id used to unregister the request or notification. Usually an id provided during the
     * register request.
     */
    public String getId() {
        return jsonData.getString("id");
    }

    public Unregistration setId(String id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * The method to unregister for.
     */
    public String getMethod() {
        return jsonData.getString("method");
    }

    public Unregistration setMethod(String method) {
        jsonData.put("method", method);
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
        Unregistration other = (Unregistration) obj;
        if (!Objects.equals(this.getId(), other.getId())) {
            return false;
        }
        if (!Objects.equals(this.getMethod(), other.getMethod())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.getId());
        hash = 17 * hash + Objects.hashCode(this.getMethod());
        return hash;
    }

    public static Unregistration create(String id, String method) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("method", method);
        return new Unregistration(json);
    }
}
