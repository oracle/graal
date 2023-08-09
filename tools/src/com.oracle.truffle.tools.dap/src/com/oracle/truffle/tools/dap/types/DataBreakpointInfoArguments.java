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
 * Arguments for 'dataBreakpointInfo' request.
 */
public class DataBreakpointInfoArguments extends JSONBase {

    DataBreakpointInfoArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Reference to the Variable container if the data breakpoint is requested for a child of the
     * container.
     */
    public Integer getVariablesReference() {
        return jsonData.has("variablesReference") ? jsonData.getInt("variablesReference") : null;
    }

    public DataBreakpointInfoArguments setVariablesReference(Integer variablesReference) {
        jsonData.putOpt("variablesReference", variablesReference);
        return this;
    }

    /**
     * The name of the Variable's child to obtain data breakpoint information for. If
     * variableReference isn't provided, this can be an expression.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public DataBreakpointInfoArguments setName(String name) {
        jsonData.put("name", name);
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
        DataBreakpointInfoArguments other = (DataBreakpointInfoArguments) obj;
        if (!Objects.equals(this.getVariablesReference(), other.getVariablesReference())) {
            return false;
        }
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getVariablesReference() != null) {
            hash = 71 * hash + Integer.hashCode(this.getVariablesReference());
        }
        hash = 71 * hash + Objects.hashCode(this.getName());
        return hash;
    }

    public static DataBreakpointInfoArguments create(String name) {
        final JSONObject json = new JSONObject();
        json.put("name", name);
        return new DataBreakpointInfoArguments(json);
    }
}
