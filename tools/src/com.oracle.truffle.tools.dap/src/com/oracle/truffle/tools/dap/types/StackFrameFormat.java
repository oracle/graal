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
 * Provides formatting information for a stack frame.
 */
public class StackFrameFormat extends ValueFormat {

    StackFrameFormat(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Displays parameters for the stack frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getParameters() {
        return jsonData.has("parameters") ? jsonData.getBoolean("parameters") : null;
    }

    public StackFrameFormat setParameters(Boolean parameters) {
        jsonData.putOpt("parameters", parameters);
        return this;
    }

    /**
     * Displays the types of parameters for the stack frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getParameterTypes() {
        return jsonData.has("parameterTypes") ? jsonData.getBoolean("parameterTypes") : null;
    }

    public StackFrameFormat setParameterTypes(Boolean parameterTypes) {
        jsonData.putOpt("parameterTypes", parameterTypes);
        return this;
    }

    /**
     * Displays the names of parameters for the stack frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getParameterNames() {
        return jsonData.has("parameterNames") ? jsonData.getBoolean("parameterNames") : null;
    }

    public StackFrameFormat setParameterNames(Boolean parameterNames) {
        jsonData.putOpt("parameterNames", parameterNames);
        return this;
    }

    /**
     * Displays the values of parameters for the stack frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getParameterValues() {
        return jsonData.has("parameterValues") ? jsonData.getBoolean("parameterValues") : null;
    }

    public StackFrameFormat setParameterValues(Boolean parameterValues) {
        jsonData.putOpt("parameterValues", parameterValues);
        return this;
    }

    /**
     * Displays the line number of the stack frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getLine() {
        return jsonData.has("line") ? jsonData.getBoolean("line") : null;
    }

    public StackFrameFormat setLine(Boolean line) {
        jsonData.putOpt("line", line);
        return this;
    }

    /**
     * Displays the module of the stack frame.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getModule() {
        return jsonData.has("module") ? jsonData.getBoolean("module") : null;
    }

    public StackFrameFormat setModule(Boolean module) {
        jsonData.putOpt("module", module);
        return this;
    }

    /**
     * Includes all stack frames, including those the debug adapter might otherwise hide.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getIncludeAll() {
        return jsonData.has("includeAll") ? jsonData.getBoolean("includeAll") : null;
    }

    public StackFrameFormat setIncludeAll(Boolean includeAll) {
        jsonData.putOpt("includeAll", includeAll);
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
        StackFrameFormat other = (StackFrameFormat) obj;
        if (!Objects.equals(this.getParameters(), other.getParameters())) {
            return false;
        }
        if (!Objects.equals(this.getParameterTypes(), other.getParameterTypes())) {
            return false;
        }
        if (!Objects.equals(this.getParameterNames(), other.getParameterNames())) {
            return false;
        }
        if (!Objects.equals(this.getParameterValues(), other.getParameterValues())) {
            return false;
        }
        if (!Objects.equals(this.getLine(), other.getLine())) {
            return false;
        }
        if (!Objects.equals(this.getModule(), other.getModule())) {
            return false;
        }
        if (!Objects.equals(this.getIncludeAll(), other.getIncludeAll())) {
            return false;
        }
        if (!Objects.equals(this.getHex(), other.getHex())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        if (this.getParameters() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getParameters());
        }
        if (this.getParameterTypes() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getParameterTypes());
        }
        if (this.getParameterNames() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getParameterNames());
        }
        if (this.getParameterValues() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getParameterValues());
        }
        if (this.getLine() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getLine());
        }
        if (this.getModule() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getModule());
        }
        if (this.getIncludeAll() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getIncludeAll());
        }
        if (this.getHex() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getHex());
        }
        return hash;
    }

    public static StackFrameFormat create() {
        final JSONObject json = new JSONObject();
        return new StackFrameFormat(json);
    }
}
