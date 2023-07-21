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
 * A Scope is a named container for variables. Optionally a scope can map to a source or a range
 * within a source.
 */
public class Scope extends JSONBase {

    Scope(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Name of the scope such as 'Arguments', 'Locals', or 'Registers'. This string is shown in the
     * UI as is and can be translated.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public Scope setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * An optional hint for how to present this scope in the UI. If this attribute is missing, the
     * scope is shown with a generic UI. Values: 'arguments': Scope contains method arguments.
     * 'locals': Scope contains local variables. 'registers': Scope contains registers. Only a
     * single 'registers' scope should be returned from a 'scopes' request. etc.
     */
    public String getPresentationHint() {
        return jsonData.optString("presentationHint", null);
    }

    public Scope setPresentationHint(String presentationHint) {
        jsonData.putOpt("presentationHint", presentationHint);
        return this;
    }

    /**
     * The variables of this scope can be retrieved by passing the value of variablesReference to
     * the VariablesRequest.
     */
    public int getVariablesReference() {
        return jsonData.getInt("variablesReference");
    }

    public Scope setVariablesReference(int variablesReference) {
        jsonData.put("variablesReference", variablesReference);
        return this;
    }

    /**
     * The number of named variables in this scope. The client can use this optional information to
     * present the variables in a paged UI and fetch them in chunks.
     */
    public Integer getNamedVariables() {
        return jsonData.has("namedVariables") ? jsonData.getInt("namedVariables") : null;
    }

    public Scope setNamedVariables(Integer namedVariables) {
        jsonData.putOpt("namedVariables", namedVariables);
        return this;
    }

    /**
     * The number of indexed variables in this scope. The client can use this optional information
     * to present the variables in a paged UI and fetch them in chunks.
     */
    public Integer getIndexedVariables() {
        return jsonData.has("indexedVariables") ? jsonData.getInt("indexedVariables") : null;
    }

    public Scope setIndexedVariables(Integer indexedVariables) {
        jsonData.putOpt("indexedVariables", indexedVariables);
        return this;
    }

    /**
     * If true, the number of variables in this scope is large or expensive to retrieve.
     */
    public boolean isExpensive() {
        return jsonData.getBoolean("expensive");
    }

    public Scope setExpensive(boolean expensive) {
        jsonData.put("expensive", expensive);
        return this;
    }

    /**
     * Optional source for this scope.
     */
    public Source getSource() {
        return jsonData.has("source") ? new Source(jsonData.optJSONObject("source")) : null;
    }

    public Scope setSource(Source source) {
        jsonData.putOpt("source", source != null ? source.jsonData : null);
        return this;
    }

    /**
     * Optional start line of the range covered by this scope.
     */
    public Integer getLine() {
        return jsonData.has("line") ? jsonData.getInt("line") : null;
    }

    public Scope setLine(Integer line) {
        jsonData.putOpt("line", line);
        return this;
    }

    /**
     * Optional start column of the range covered by this scope.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public Scope setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * Optional end line of the range covered by this scope.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public Scope setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * Optional end column of the range covered by this scope.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public Scope setEndColumn(Integer endColumn) {
        jsonData.putOpt("endColumn", endColumn);
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
        Scope other = (Scope) obj;
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getPresentationHint(), other.getPresentationHint())) {
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
        if (this.isExpensive() != other.isExpensive()) {
            return false;
        }
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (!Objects.equals(this.getLine(), other.getLine())) {
            return false;
        }
        if (!Objects.equals(this.getColumn(), other.getColumn())) {
            return false;
        }
        if (!Objects.equals(this.getEndLine(), other.getEndLine())) {
            return false;
        }
        if (!Objects.equals(this.getEndColumn(), other.getEndColumn())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getName());
        if (this.getPresentationHint() != null) {
            hash = 41 * hash + Objects.hashCode(this.getPresentationHint());
        }
        hash = 41 * hash + Integer.hashCode(this.getVariablesReference());
        if (this.getNamedVariables() != null) {
            hash = 41 * hash + Integer.hashCode(this.getNamedVariables());
        }
        if (this.getIndexedVariables() != null) {
            hash = 41 * hash + Integer.hashCode(this.getIndexedVariables());
        }
        hash = 41 * hash + Boolean.hashCode(this.isExpensive());
        if (this.getSource() != null) {
            hash = 41 * hash + Objects.hashCode(this.getSource());
        }
        if (this.getLine() != null) {
            hash = 41 * hash + Integer.hashCode(this.getLine());
        }
        if (this.getColumn() != null) {
            hash = 41 * hash + Integer.hashCode(this.getColumn());
        }
        if (this.getEndLine() != null) {
            hash = 41 * hash + Integer.hashCode(this.getEndLine());
        }
        if (this.getEndColumn() != null) {
            hash = 41 * hash + Integer.hashCode(this.getEndColumn());
        }
        return hash;
    }

    public static Scope create(String name, Integer variablesReference, Boolean expensive) {
        final JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("variablesReference", variablesReference);
        json.put("expensive", expensive);
        return new Scope(json);
    }
}
