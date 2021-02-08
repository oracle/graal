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
 * Arguments for 'setVariable' request.
 */
public class SetVariableArguments extends JSONBase {

    SetVariableArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The reference of the variable container.
     */
    public int getVariablesReference() {
        return jsonData.getInt("variablesReference");
    }

    public SetVariableArguments setVariablesReference(int variablesReference) {
        jsonData.put("variablesReference", variablesReference);
        return this;
    }

    /**
     * The name of the variable in the container.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public SetVariableArguments setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * The value of the variable.
     */
    public String getValue() {
        return jsonData.getString("value");
    }

    public SetVariableArguments setValue(String value) {
        jsonData.put("value", value);
        return this;
    }

    /**
     * Specifies details on how to format the response value.
     */
    public ValueFormat getFormat() {
        return jsonData.has("format") ? new ValueFormat(jsonData.optJSONObject("format")) : null;
    }

    public SetVariableArguments setFormat(ValueFormat format) {
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
        SetVariableArguments other = (SetVariableArguments) obj;
        if (this.getVariablesReference() != other.getVariablesReference()) {
            return false;
        }
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getValue(), other.getValue())) {
            return false;
        }
        if (!Objects.equals(this.getFormat(), other.getFormat())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 31 * hash + Integer.hashCode(this.getVariablesReference());
        hash = 31 * hash + Objects.hashCode(this.getName());
        hash = 31 * hash + Objects.hashCode(this.getValue());
        if (this.getFormat() != null) {
            hash = 31 * hash + Objects.hashCode(this.getFormat());
        }
        return hash;
    }

    public static SetVariableArguments create(Integer variablesReference, String name, String value) {
        final JSONObject json = new JSONObject();
        json.put("variablesReference", variablesReference);
        json.put("name", name);
        json.put("value", value);
        return new SetVariableArguments(json);
    }
}
