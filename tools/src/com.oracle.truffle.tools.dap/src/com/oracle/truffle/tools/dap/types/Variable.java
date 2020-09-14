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
 * A Variable is a name/value pair. Optionally a variable can have a 'type' that is shown if space
 * permits or when hovering over the variable's name. An optional 'kind' is used to render
 * additional properties of the variable, e.g. different icons can be used to indicate that a
 * variable is public or private. If the value is structured (has children), a handle is provided to
 * retrieve the children with the VariablesRequest. If the number of named or indexed children is
 * large, the numbers should be returned via the optional 'namedVariables' and 'indexedVariables'
 * attributes. The client can use this optional information to present the children in a paged UI
 * and fetch them in chunks.
 */
public class Variable extends JSONBase {

    Variable(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The variable's name.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public Variable setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * The variable's value. This can be a multi-line text, e.g. for a function the body of a
     * function.
     */
    public String getValue() {
        return jsonData.getString("value");
    }

    public Variable setValue(String value) {
        jsonData.put("value", value);
        return this;
    }

    /**
     * The type of the variable's value. Typically shown in the UI when hovering over the value.
     * This attribute should only be returned by a debug adapter if the client has passed the value
     * true for the 'supportsVariableType' capability of the 'initialize' request.
     */
    public String getType() {
        return jsonData.optString("type", null);
    }

    public Variable setType(String type) {
        jsonData.putOpt("type", type);
        return this;
    }

    /**
     * Properties of a variable that can be used to determine how to render the variable in the UI.
     */
    public VariablePresentationHint getPresentationHint() {
        return jsonData.has("presentationHint") ? new VariablePresentationHint(jsonData.optJSONObject("presentationHint")) : null;
    }

    public Variable setPresentationHint(VariablePresentationHint presentationHint) {
        jsonData.putOpt("presentationHint", presentationHint != null ? presentationHint.jsonData : null);
        return this;
    }

    /**
     * Optional evaluatable name of this variable which can be passed to the 'EvaluateRequest' to
     * fetch the variable's value.
     */
    public String getEvaluateName() {
        return jsonData.optString("evaluateName", null);
    }

    public Variable setEvaluateName(String evaluateName) {
        jsonData.putOpt("evaluateName", evaluateName);
        return this;
    }

    /**
     * If variablesReference is > 0, the variable is structured and its children can be retrieved by
     * passing variablesReference to the VariablesRequest.
     */
    public int getVariablesReference() {
        return jsonData.getInt("variablesReference");
    }

    public Variable setVariablesReference(int variablesReference) {
        jsonData.put("variablesReference", variablesReference);
        return this;
    }

    /**
     * The number of named child variables. The client can use this optional information to present
     * the children in a paged UI and fetch them in chunks.
     */
    public Integer getNamedVariables() {
        return jsonData.has("namedVariables") ? jsonData.getInt("namedVariables") : null;
    }

    public Variable setNamedVariables(Integer namedVariables) {
        jsonData.putOpt("namedVariables", namedVariables);
        return this;
    }

    /**
     * The number of indexed child variables. The client can use this optional information to
     * present the children in a paged UI and fetch them in chunks.
     */
    public Integer getIndexedVariables() {
        return jsonData.has("indexedVariables") ? jsonData.getInt("indexedVariables") : null;
    }

    public Variable setIndexedVariables(Integer indexedVariables) {
        jsonData.putOpt("indexedVariables", indexedVariables);
        return this;
    }

    /**
     * Optional memory reference for the variable if the variable represents executable code, such
     * as a function pointer. This attribute is only required if the client has passed the value
     * true for the 'supportsMemoryReferences' capability of the 'initialize' request.
     */
    public String getMemoryReference() {
        return jsonData.optString("memoryReference", null);
    }

    public Variable setMemoryReference(String memoryReference) {
        jsonData.putOpt("memoryReference", memoryReference);
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
        Variable other = (Variable) obj;
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getValue(), other.getValue())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (!Objects.equals(this.getPresentationHint(), other.getPresentationHint())) {
            return false;
        }
        if (!Objects.equals(this.getEvaluateName(), other.getEvaluateName())) {
            return false;
        }
        if (this.getVariablesReference() != other.getVariablesReference()) {
            return false;
        }
        if (!Objects.equals(this.getNamedVariables(), other.getNamedVariables())) {
            return false;
        }
        if (!Objects.equals(this.getIndexedVariables(), other.getIndexedVariables())) {
            return false;
        }
        if (!Objects.equals(this.getMemoryReference(), other.getMemoryReference())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.getName());
        hash = 29 * hash + Objects.hashCode(this.getValue());
        if (this.getType() != null) {
            hash = 29 * hash + Objects.hashCode(this.getType());
        }
        if (this.getPresentationHint() != null) {
            hash = 29 * hash + Objects.hashCode(this.getPresentationHint());
        }
        if (this.getEvaluateName() != null) {
            hash = 29 * hash + Objects.hashCode(this.getEvaluateName());
        }
        hash = 29 * hash + Integer.hashCode(this.getVariablesReference());
        if (this.getNamedVariables() != null) {
            hash = 29 * hash + Integer.hashCode(this.getNamedVariables());
        }
        if (this.getIndexedVariables() != null) {
            hash = 29 * hash + Integer.hashCode(this.getIndexedVariables());
        }
        if (this.getMemoryReference() != null) {
            hash = 29 * hash + Objects.hashCode(this.getMemoryReference());
        }
        return hash;
    }

    public static Variable create(String name, String value, Integer variablesReference) {
        final JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("value", value);
        json.put("variablesReference", variablesReference);
        return new Variable(json);
    }
}
