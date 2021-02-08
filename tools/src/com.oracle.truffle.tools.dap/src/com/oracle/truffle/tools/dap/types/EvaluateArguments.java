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
 * Arguments for 'evaluate' request.
 */
public class EvaluateArguments extends JSONBase {

    EvaluateArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The expression to evaluate.
     */
    public String getExpression() {
        return jsonData.getString("expression");
    }

    public EvaluateArguments setExpression(String expression) {
        jsonData.put("expression", expression);
        return this;
    }

    /**
     * Evaluate the expression in the scope of this stack frame. If not specified, the expression is
     * evaluated in the global scope.
     */
    public Integer getFrameId() {
        return jsonData.has("frameId") ? jsonData.getInt("frameId") : null;
    }

    public EvaluateArguments setFrameId(Integer frameId) {
        jsonData.putOpt("frameId", frameId);
        return this;
    }

    /**
     * The context in which the evaluate request is run. Values: 'watch': evaluate is run in a
     * watch. 'repl': evaluate is run from REPL console. 'hover': evaluate is run from a data hover.
     * 'clipboard': evaluate is run to generate the value that will be stored in the clipboard. The
     * attribute is only honored by a debug adapter if the capability 'supportsClipboardContext' is
     * true. etc.
     */
    public String getContext() {
        return jsonData.optString("context", null);
    }

    public EvaluateArguments setContext(String context) {
        jsonData.putOpt("context", context);
        return this;
    }

    /**
     * Specifies details on how to format the Evaluate result. The attribute is only honored by a
     * debug adapter if the capability 'supportsValueFormattingOptions' is true.
     */
    public ValueFormat getFormat() {
        return jsonData.has("format") ? new ValueFormat(jsonData.optJSONObject("format")) : null;
    }

    public EvaluateArguments setFormat(ValueFormat format) {
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
        EvaluateArguments other = (EvaluateArguments) obj;
        if (!Objects.equals(this.getExpression(), other.getExpression())) {
            return false;
        }
        if (!Objects.equals(this.getFrameId(), other.getFrameId())) {
            return false;
        }
        if (!Objects.equals(this.getContext(), other.getContext())) {
            return false;
        }
        if (!Objects.equals(this.getFormat(), other.getFormat())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.getExpression());
        if (this.getFrameId() != null) {
            hash = 41 * hash + Integer.hashCode(this.getFrameId());
        }
        if (this.getContext() != null) {
            hash = 41 * hash + Objects.hashCode(this.getContext());
        }
        if (this.getFormat() != null) {
            hash = 41 * hash + Objects.hashCode(this.getFormat());
        }
        return hash;
    }

    public static EvaluateArguments create(String expression) {
        final JSONObject json = new JSONObject();
        json.put("expression", expression);
        return new EvaluateArguments(json);
    }
}
