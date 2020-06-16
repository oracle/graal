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
 * A selection range represents a part of a selection hierarchy. A selection range may have a parent
 * selection range that contains it.
 */
public class SelectionRange extends JSONBase {

    SelectionRange(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The [range](#Range) of this selection range.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public SelectionRange setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The parent selection range containing this range. Therefore `parent.range` must contain
     * `this.range`.
     */
    public SelectionRange getParent() {
        return jsonData.has("parent") ? new SelectionRange(jsonData.optJSONObject("parent")) : null;
    }

    public SelectionRange setParent(SelectionRange parent) {
        jsonData.putOpt("parent", parent != null ? parent.jsonData : null);
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
        SelectionRange other = (SelectionRange) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getParent(), other.getParent())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 43 * hash + Objects.hashCode(this.getRange());
        if (this.getParent() != null) {
            hash = 43 * hash + Objects.hashCode(this.getParent());
        }
        return hash;
    }

    /**
     * Creates a new SelectionRange.
     *
     * @param range the range.
     * @param parent an optional parent.
     */
    public static SelectionRange create(Range range, SelectionRange parent) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.putOpt("parent", parent != null ? parent.jsonData : null);
        return new SelectionRange(json);
    }
}
