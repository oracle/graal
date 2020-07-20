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
 * The watched files change notification's parameters.
 */
public class DidChangeWatchedFilesParams extends JSONBase {

    DidChangeWatchedFilesParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The actual file events.
     */
    public List<FileEvent> getChanges() {
        final JSONArray json = jsonData.getJSONArray("changes");
        final List<FileEvent> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new FileEvent(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public DidChangeWatchedFilesParams setChanges(List<FileEvent> changes) {
        final JSONArray json = new JSONArray();
        for (FileEvent fileEvent : changes) {
            json.put(fileEvent.jsonData);
        }
        jsonData.put("changes", json);
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
        DidChangeWatchedFilesParams other = (DidChangeWatchedFilesParams) obj;
        if (!Objects.equals(this.getChanges(), other.getChanges())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.getChanges());
        return hash;
    }

    public static DidChangeWatchedFilesParams create(List<FileEvent> changes) {
        final JSONObject json = new JSONObject();
        JSONArray changesJsonArr = new JSONArray();
        for (FileEvent fileEvent : changes) {
            changesJsonArr.put(fileEvent.jsonData);
        }
        json.put("changes", changesJsonArr);
        return new DidChangeWatchedFilesParams(json);
    }
}
