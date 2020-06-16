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

public class WorkspaceFolder extends JSONBase {

    WorkspaceFolder(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The associated URI for this workspace folder.
     */
    public String getUri() {
        return jsonData.getString("uri");
    }

    public WorkspaceFolder setUri(String uri) {
        jsonData.put("uri", uri);
        return this;
    }

    /**
     * The name of the workspace folder. Used to refer to this workspace folder in the user
     * interface.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public WorkspaceFolder setName(String name) {
        jsonData.put("name", name);
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
        WorkspaceFolder other = (WorkspaceFolder) obj;
        if (!Objects.equals(this.getUri(), other.getUri())) {
            return false;
        }
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Objects.hashCode(this.getUri());
        hash = 19 * hash + Objects.hashCode(this.getName());
        return hash;
    }

    public static WorkspaceFolder create(String uri, String name) {
        final JSONObject json = new JSONObject();
        json.put("uri", uri);
        json.put("name", name);
        return new WorkspaceFolder(json);
    }
}
