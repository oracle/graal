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

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * Arguments for 'setExpression' request.
 */
public class SetExpressionArguments extends JSONBase {

    SetExpressionArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The l-value expression to assign to.
     */
    public String getExpression() {
        return jsonData.getString("expression");
    }

    public SetExpressionArguments setExpression(String expression) {
        jsonData.put("expression", expression);
        return this;
    }

    /**
     * The value expression to assign to the l-value expression.
     */
    public String getValue() {
        return jsonData.getString("value");
    }

    public SetExpressionArguments setValue(String value) {
        jsonData.put("value", value);
        return this;
    }

    /**
     * Evaluate the expressions in the scope of this stack frame. If not specified, the expressions
     * are evaluated in the global scope.
     */
    public Integer getFrameId() {
        return jsonData.has("frameId") ? jsonData.getInt("frameId") : null;
    }

    public SetExpressionArguments setFrameId(Integer frameId) {
        jsonData.putOpt("frameId", frameId);
        return this;
    }

    /**
     * Specifies how the resulting value should be formatted.
     */
    public ValueFormat getFormat() {
        return jsonData.has("format") ? new ValueFormat(jsonData.optJSONObject("format")) : null;
    }

    public SetExpressionArguments setFormat(ValueFormat format) {
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
        SetExpressionArguments other = (SetExpressionArguments) obj;
        if (!Objects.equals(this.getExpression(), other.getExpression())) {
            return false;
        }
        if (!Objects.equals(this.getValue(), other.getValue())) {
            return false;
        }
        if (!Objects.equals(this.getFrameId(), other.getFrameId())) {
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
        hash = 53 * hash + Objects.hashCode(this.getExpression());
        hash = 53 * hash + Objects.hashCode(this.getValue());
        if (this.getFrameId() != null) {
            hash = 53 * hash + Integer.hashCode(this.getFrameId());
        }
        if (this.getFormat() != null) {
            hash = 53 * hash + Objects.hashCode(this.getFormat());
        }
        return hash;
    }

    public static SetExpressionArguments create(String expression, String value) {
        final JSONObject json = new JSONObject();
        json.put("expression", expression);
        json.put("value", value);
        return new SetExpressionArguments(json);
    }
}
