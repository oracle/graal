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
import java.util.List;
import java.util.Objects;

/**
 * The workspace folder change event.
 */
public class WorkspaceFoldersChangeEvent extends JSONBase {

    WorkspaceFoldersChangeEvent(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The array of added workspace folders.
     */
    public List<WorkspaceFolder> getAdded() {
        final JSONArray json = jsonData.getJSONArray("added");
        final List<WorkspaceFolder> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new WorkspaceFolder(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public WorkspaceFoldersChangeEvent setAdded(List<WorkspaceFolder> added) {
        final JSONArray json = new JSONArray();
        for (WorkspaceFolder workspaceFolder : added) {
            json.put(workspaceFolder.jsonData);
        }
        jsonData.put("added", json);
        return this;
    }

    /**
     * The array of the removed workspace folders.
     */
    public List<WorkspaceFolder> getRemoved() {
        final JSONArray json = jsonData.getJSONArray("removed");
        final List<WorkspaceFolder> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new WorkspaceFolder(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public WorkspaceFoldersChangeEvent setRemoved(List<WorkspaceFolder> removed) {
        final JSONArray json = new JSONArray();
        for (WorkspaceFolder workspaceFolder : removed) {
            json.put(workspaceFolder.jsonData);
        }
        jsonData.put("removed", json);
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
        WorkspaceFoldersChangeEvent other = (WorkspaceFoldersChangeEvent) obj;
        if (!Objects.equals(this.getAdded(), other.getAdded())) {
            return false;
        }
        if (!Objects.equals(this.getRemoved(), other.getRemoved())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.getAdded());
        hash = 89 * hash + Objects.hashCode(this.getRemoved());
        return hash;
    }

    public static WorkspaceFoldersChangeEvent create(List<WorkspaceFolder> added, List<WorkspaceFolder> removed) {
        final JSONObject json = new JSONObject();
        JSONArray addedJsonArr = new JSONArray();
        for (WorkspaceFolder workspaceFolder : added) {
            addedJsonArr.put(workspaceFolder.jsonData);
        }
        json.put("added", addedJsonArr);
        JSONArray removedJsonArr = new JSONArray();
        for (WorkspaceFolder workspaceFolder : removed) {
            removedJsonArr.put(workspaceFolder.jsonData);
        }
        json.put("removed", removedJsonArr);
        return new WorkspaceFoldersChangeEvent(json);
    }
}
