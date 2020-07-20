/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

public class Coverage extends JSONBase {

    Coverage(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Covered ranges.
     */
    public List<Range> getCovered() {
        final JSONArray json = jsonData.getJSONArray("covered");
        final List<Range> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Range(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Coverage setCovered(List<Range> covered) {
        final JSONArray json = new JSONArray();
        for (Range range : covered) {
            json.put(range.jsonData);
        }
        jsonData.put("covered", json);
        return this;
    }

    /**
     * Uncovered ranges.
     */
    public List<Range> getUncovered() {
        final JSONArray json = jsonData.getJSONArray("uncovered");
        final List<Range> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Range(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Coverage setUnovered(List<Range> uncovered) {
        final JSONArray json = new JSONArray();
        for (Range range : uncovered) {
            json.put(range.jsonData);
        }
        jsonData.put("uncovered", json);
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
        Coverage other = (Coverage) obj;
        if (!Objects.equals(this.getCovered(), other.getCovered())) {
            return false;
        }
        if (!Objects.equals(this.getUncovered(), other.getUncovered())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.getCovered());
        hash = 97 * hash + Objects.hashCode(this.getUncovered());
        return hash;
    }

    public static Coverage create(List<Range> covered, List<Range> uncovered) {
        final JSONObject json = new JSONObject();
        JSONArray itemsJsonArr = new JSONArray();
        for (Range range : covered) {
            itemsJsonArr.put(range.jsonData);
        }
        json.put("covered", itemsJsonArr);
        itemsJsonArr = new JSONArray();
        for (Range range : uncovered) {
            itemsJsonArr.put(range.jsonData);
        }
        json.put("uncovered", itemsJsonArr);
        return new Coverage(json);
    }
}
