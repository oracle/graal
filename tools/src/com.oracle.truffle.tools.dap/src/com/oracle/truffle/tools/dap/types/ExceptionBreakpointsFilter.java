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
 * An ExceptionBreakpointsFilter is shown in the UI as an option for configuring how exceptions are
 * dealt with.
 */
public class ExceptionBreakpointsFilter extends JSONBase {

    ExceptionBreakpointsFilter(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The internal ID of the filter. This value is passed to the setExceptionBreakpoints request.
     */
    public String getFilter() {
        return jsonData.getString("filter");
    }

    public ExceptionBreakpointsFilter setFilter(String filter) {
        jsonData.put("filter", filter);
        return this;
    }

    /**
     * The name of the filter. This will be shown in the UI.
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public ExceptionBreakpointsFilter setLabel(String label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * Initial value of the filter. If not specified a value 'false' is assumed.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDefault() {
        return jsonData.has("default") ? jsonData.getBoolean("default") : null;
    }

    public ExceptionBreakpointsFilter setDefault(Boolean defaultValue) {
        jsonData.putOpt("default", defaultValue);
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
        ExceptionBreakpointsFilter other = (ExceptionBreakpointsFilter) obj;
        if (!Objects.equals(this.getFilter(), other.getFilter())) {
            return false;
        }
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getDefault(), other.getDefault())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 19 * hash + Objects.hashCode(this.getFilter());
        hash = 19 * hash + Objects.hashCode(this.getLabel());
        if (this.getDefault() != null) {
            hash = 19 * hash + Boolean.hashCode(this.getDefault());
        }
        return hash;
    }

    public static ExceptionBreakpointsFilter create(String filter, String label) {
        final JSONObject json = new JSONObject();
        json.put("filter", filter);
        json.put("label", label);
        return new ExceptionBreakpointsFilter(json);
    }
}
