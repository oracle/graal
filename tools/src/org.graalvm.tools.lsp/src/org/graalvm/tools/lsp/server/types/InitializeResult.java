/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * The result returned from an initialize request.
 */
public class InitializeResult {

    final JSONObject jsonData;

    InitializeResult(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The capabilities the language server provides.
     */
    public ServerCapabilities getCapabilities() {
        return new ServerCapabilities(jsonData.getJSONObject("capabilities"));
    }

    public InitializeResult setCapabilities(ServerCapabilities capabilities) {
        jsonData.put("capabilities", capabilities.jsonData);
        return this;
    }

    /**
     * Custom initialization results.
     */
    public Object get(String key) {
        return jsonData.get(key);
    }

    public InitializeResult set(String key, Object value) {
        jsonData.put(key, value);
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
        InitializeResult other = (InitializeResult) obj;
        if (!Objects.equals(this.getCapabilities(), other.getCapabilities())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.getCapabilities());
        return hash;
    }

    public static InitializeResult create(ServerCapabilities capabilities) {
        final JSONObject json = new JSONObject();
        json.put("capabilities", capabilities.jsonData);
        return new InitializeResult(json);
    }
}
