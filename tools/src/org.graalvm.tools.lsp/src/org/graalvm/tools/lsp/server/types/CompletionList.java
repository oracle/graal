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
 * Represents a collection of [completion items](#CompletionItem) to be presented in the editor.
 */
public class CompletionList extends JSONBase {

    CompletionList(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * This list it not complete. Further typing results in recomputing this list.
     */
    public boolean isIncomplete() {
        return jsonData.getBoolean("isIncomplete");
    }

    public CompletionList setIncomplete(boolean isIncomplete) {
        jsonData.put("isIncomplete", isIncomplete);
        return this;
    }

    /**
     * The completion items.
     */
    public List<CompletionItem> getItems() {
        final JSONArray json = jsonData.getJSONArray("items");
        final List<CompletionItem> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new CompletionItem(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public CompletionList setItems(List<CompletionItem> items) {
        final JSONArray json = new JSONArray();
        for (CompletionItem completionItem : items) {
            json.put(completionItem.jsonData);
        }
        jsonData.put("items", json);
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
        CompletionList other = (CompletionList) obj;
        if (this.isIncomplete() != other.isIncomplete()) {
            return false;
        }
        if (!Objects.equals(this.getItems(), other.getItems())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Boolean.hashCode(this.isIncomplete());
        hash = 59 * hash + Objects.hashCode(this.getItems());
        return hash;
    }

    /**
     * Creates a new completion list.
     *
     * @param items The completion items.
     * @param isIncomplete The list is not complete.
     */
    public static CompletionList create(List<CompletionItem> items, boolean isIncomplete) {
        final JSONObject json = new JSONObject();
        json.put("isIncomplete", isIncomplete);
        JSONArray itemsJsonArr = new JSONArray();
        for (CompletionItem completionItem : items) {
            itemsJsonArr.put(completionItem.jsonData);
        }
        json.put("items", itemsJsonArr);
        return new CompletionList(json);
    }
}
