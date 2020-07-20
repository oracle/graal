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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A workspace edit represents changes to many resources managed in the workspace. The edit should
 * either provide `changes` or `documentChanges`. If documentChanges are present they are preferred
 * over `changes` if the client can handle versioned document edits.
 */
public class WorkspaceEdit extends JSONBase {

    WorkspaceEdit(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Holds changes to existing resources.
     */
    public Map<String, List<TextEdit>> getChanges() {
        final JSONObject json = jsonData.optJSONObject("changes");
        if (json == null) {
            return null;
        }
        final Map<String, List<TextEdit>> map = new HashMap<>(json.length());
        for (String key : json.keySet()) {
            final JSONArray jsonArr = json.getJSONArray(key);
            final List<TextEdit> list = new ArrayList<>(jsonArr.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(new TextEdit(jsonArr.getJSONObject(i)));
            }
            map.put(key, Collections.unmodifiableList(list));
        }
        return map;
    }

    public WorkspaceEdit setChanges(Map<String, List<TextEdit>> changes) {
        if (changes != null) {
            final JSONObject json = new JSONObject();
            for (Map.Entry<String, List<TextEdit>> entry : changes.entrySet()) {
                final JSONArray jsonArr = new JSONArray();
                for (TextEdit textEdit : entry.getValue()) {
                    jsonArr.put(textEdit.jsonData);
                }
                json.put(entry.getKey(), jsonArr);
            }
            jsonData.put("changes", json);
        }
        return this;
    }

    /**
     * Depending on the client capability `workspace.workspaceEdit.resourceOperations` document
     * changes are either an array of `TextDocumentEdit`s to express changes to n different text
     * documents where each text document edit addresses a specific version of a text document. Or
     * it can contain above `TextDocumentEdit`s mixed with create, rename and delete file / folder
     * operations.
     *
     * Whether a client supports versioned document edits is expressed via
     * `workspace.workspaceEdit.documentChanges` client capability.
     *
     * If a client neither supports `documentChanges` nor
     * `workspace.workspaceEdit.resourceOperations` then only plain `TextEdit`s using the `changes`
     * property are supported.
     */
    public List<Object> getDocumentChanges() {
        final JSONArray json = jsonData.optJSONArray("documentChanges");
        if (json == null) {
            return null;
        }
        final List<Object> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.get(i));
        }
        return Collections.unmodifiableList(list);
    }

    public WorkspaceEdit setDocumentChanges(List<Object> documentChanges) {
        if (documentChanges != null) {
            final JSONArray json = new JSONArray();
            for (Object object : documentChanges) {
                json.put(object);
            }
            jsonData.put("documentChanges", json);
        }
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
        WorkspaceEdit other = (WorkspaceEdit) obj;
        if (!Objects.equals(this.getChanges(), other.getChanges())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentChanges(), other.getDocumentChanges())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getChanges() != null) {
            hash = 97 * hash + Objects.hashCode(this.getChanges());
        }
        if (this.getDocumentChanges() != null) {
            hash = 97 * hash + Objects.hashCode(this.getDocumentChanges());
        }
        return hash;
    }

    public static WorkspaceEdit create() {
        final JSONObject json = new JSONObject();
        return new WorkspaceEdit(json);
    }
}
