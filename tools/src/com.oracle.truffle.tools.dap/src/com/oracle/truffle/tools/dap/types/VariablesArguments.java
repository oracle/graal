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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * Arguments for 'variables' request.
 */
public class VariablesArguments extends JSONBase {

    VariablesArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The Variable reference.
     */
    public int getVariablesReference() {
        return jsonData.getInt("variablesReference");
    }

    public VariablesArguments setVariablesReference(int variablesReference) {
        jsonData.put("variablesReference", variablesReference);
        return this;
    }

    /**
     * Optional filter to limit the child variables to either named or indexed. If omitted, both
     * types are fetched.
     */
    public String getFilter() {
        return jsonData.optString("filter", null);
    }

    public VariablesArguments setFilter(String filter) {
        jsonData.putOpt("filter", filter);
        return this;
    }

    /**
     * The index of the first variable to return; if omitted children start at 0.
     */
    public Integer getStart() {
        return jsonData.has("start") ? jsonData.getInt("start") : null;
    }

    public VariablesArguments setStart(Integer start) {
        jsonData.putOpt("start", start);
        return this;
    }

    /**
     * The number of variables to return. If count is missing or 0, all variables are returned.
     */
    public Integer getCount() {
        return jsonData.has("count") ? jsonData.getInt("count") : null;
    }

    public VariablesArguments setCount(Integer count) {
        jsonData.putOpt("count", count);
        return this;
    }

    /**
     * Specifies details on how to format the Variable values. The attribute is only honored by a
     * debug adapter if the capability 'supportsValueFormattingOptions' is true.
     */
    public ValueFormat getFormat() {
        return jsonData.has("format") ? new ValueFormat(jsonData.optJSONObject("format")) : null;
    }

    public VariablesArguments setFormat(ValueFormat format) {
        jsonData.putOpt("format", format != null ? format.jsonData : null);
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
        VariablesArguments other = (VariablesArguments) obj;
        if (this.getVariablesReference() != other.getVariablesReference()) {
            return false;
        }
        if (!Objects.equals(this.getFilter(), other.getFilter())) {
            return false;
        }
        if (!Objects.equals(this.getStart(), other.getStart())) {
            return false;
        }
        if (!Objects.equals(this.getCount(), other.getCount())) {
            return false;
        }
        if (!Objects.equals(this.getFormat(), other.getFormat())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 43 * hash + Integer.hashCode(this.getVariablesReference());
        if (this.getFilter() != null) {
            hash = 43 * hash + Objects.hashCode(this.getFilter());
        }
        if (this.getStart() != null) {
            hash = 43 * hash + Integer.hashCode(this.getStart());
        }
        if (this.getCount() != null) {
            hash = 43 * hash + Integer.hashCode(this.getCount());
        }
        if (this.getFormat() != null) {
            hash = 43 * hash + Objects.hashCode(this.getFormat());
        }
        return hash;
    }

    public static VariablesArguments create(Integer variablesReference) {
        final JSONObject json = new JSONObject();
        json.put("variablesReference", variablesReference);
        return new VariablesArguments(json);
    }
}
