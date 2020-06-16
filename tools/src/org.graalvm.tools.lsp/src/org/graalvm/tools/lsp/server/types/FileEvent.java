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
 * An event describing a file change.
 */
public class FileEvent extends JSONBase {

    FileEvent(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The file's uri.
     */
    public String getUri() {
        return jsonData.getString("uri");
    }

    public FileEvent setUri(String uri) {
        jsonData.put("uri", uri);
        return this;
    }

    /**
     * The change type.
     */
    public FileChangeType getType() {
        return FileChangeType.get(jsonData.getInt("type"));
    }

    public FileEvent setType(FileChangeType type) {
        jsonData.put("type", type.getIntValue());
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
        FileEvent other = (FileEvent) obj;
        if (!Objects.equals(this.getUri(), other.getUri())) {
            return false;
        }
        if (this.getType() != other.getType()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 97 * hash + Objects.hashCode(this.getUri());
        hash = 97 * hash + Objects.hashCode(this.getType());
        return hash;
    }

    public static FileEvent create(String uri, FileChangeType type) {
        final JSONObject json = new JSONObject();
        json.put("uri", uri);
        json.put("type", type.getIntValue());
        return new FileEvent(json);
    }
}
