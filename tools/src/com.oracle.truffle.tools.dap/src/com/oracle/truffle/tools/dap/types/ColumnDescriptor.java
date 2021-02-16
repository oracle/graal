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
 * A ColumnDescriptor specifies what module attribute to show in a column of the ModulesView, how to
 * format it, and what the column's label should be. It is only used if the underlying UI actually
 * supports this level of customization.
 */
public class ColumnDescriptor extends JSONBase {

    ColumnDescriptor(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Name of the attribute rendered in this column.
     */
    public String getAttributeName() {
        return jsonData.getString("attributeName");
    }

    public ColumnDescriptor setAttributeName(String attributeName) {
        jsonData.put("attributeName", attributeName);
        return this;
    }

    /**
     * Header UI label of column.
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public ColumnDescriptor setLabel(String label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * Format to use for the rendered values in this column. TBD how the format strings looks like.
     */
    public String getFormat() {
        return jsonData.optString("format", null);
    }

    public ColumnDescriptor setFormat(String format) {
        jsonData.putOpt("format", format);
        return this;
    }

    /**
     * Datatype of values in this column. Defaults to 'string' if not specified.
     */
    public String getType() {
        return jsonData.optString("type", null);
    }

    public ColumnDescriptor setType(String type) {
        jsonData.putOpt("type", type);
        return this;
    }

    /**
     * Width of this column in characters (hint only).
     */
    public Integer getWidth() {
        return jsonData.has("width") ? jsonData.getInt("width") : null;
    }

    public ColumnDescriptor setWidth(Integer width) {
        jsonData.putOpt("width", width);
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
        ColumnDescriptor other = (ColumnDescriptor) obj;
        if (!Objects.equals(this.getAttributeName(), other.getAttributeName())) {
            return false;
        }
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getFormat(), other.getFormat())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (!Objects.equals(this.getWidth(), other.getWidth())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.getAttributeName());
        hash = 67 * hash + Objects.hashCode(this.getLabel());
        if (this.getFormat() != null) {
            hash = 67 * hash + Objects.hashCode(this.getFormat());
        }
        if (this.getType() != null) {
            hash = 67 * hash + Objects.hashCode(this.getType());
        }
        if (this.getWidth() != null) {
            hash = 67 * hash + Integer.hashCode(this.getWidth());
        }
        return hash;
    }

    public static ColumnDescriptor create(String attributeName, String label) {
        final JSONObject json = new JSONObject();
        json.put("attributeName", attributeName);
        json.put("label", label);
        return new ColumnDescriptor(json);
    }
}
