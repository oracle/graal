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
 * Properties of a data breakpoint passed to the setDataBreakpoints request.
 */
public class DataBreakpoint extends JSONBase {

    DataBreakpoint(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * An id representing the data. This id is returned from the dataBreakpointInfo request.
     */
    public String getDataId() {
        return jsonData.getString("dataId");
    }

    public DataBreakpoint setDataId(String dataId) {
        jsonData.put("dataId", dataId);
        return this;
    }

    /**
     * The access type of the data.
     */
    public String getAccessType() {
        return jsonData.optString("accessType", null);
    }

    public DataBreakpoint setAccessType(String accessType) {
        jsonData.putOpt("accessType", accessType);
        return this;
    }

    /**
     * An optional expression for conditional breakpoints.
     */
    public String getCondition() {
        return jsonData.optString("condition", null);
    }

    public DataBreakpoint setCondition(String condition) {
        jsonData.putOpt("condition", condition);
        return this;
    }

    /**
     * An optional expression that controls how many hits of the breakpoint are ignored. The backend
     * is expected to interpret the expression as needed.
     */
    public String getHitCondition() {
        return jsonData.optString("hitCondition", null);
    }

    public DataBreakpoint setHitCondition(String hitCondition) {
        jsonData.putOpt("hitCondition", hitCondition);
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
        DataBreakpoint other = (DataBreakpoint) obj;
        if (!Objects.equals(this.getDataId(), other.getDataId())) {
            return false;
        }
        if (!Objects.equals(this.getAccessType(), other.getAccessType())) {
            return false;
        }
        if (!Objects.equals(this.getCondition(), other.getCondition())) {
            return false;
        }
        if (!Objects.equals(this.getHitCondition(), other.getHitCondition())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(this.getDataId());
        if (this.getAccessType() != null) {
            hash = 47 * hash + Objects.hashCode(this.getAccessType());
        }
        if (this.getCondition() != null) {
            hash = 47 * hash + Objects.hashCode(this.getCondition());
        }
        if (this.getHitCondition() != null) {
            hash = 47 * hash + Objects.hashCode(this.getHitCondition());
        }
        return hash;
    }

    public static DataBreakpoint create(String dataId) {
        final JSONObject json = new JSONObject();
        json.put("dataId", dataId);
        return new DataBreakpoint(json);
    }
}
