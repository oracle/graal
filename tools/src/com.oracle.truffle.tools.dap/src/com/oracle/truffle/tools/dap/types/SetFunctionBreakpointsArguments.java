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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Arguments for 'setFunctionBreakpoints' request.
 */
public class SetFunctionBreakpointsArguments extends JSONBase {

    SetFunctionBreakpointsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The function names of the breakpoints.
     */
    public List<FunctionBreakpoint> getBreakpoints() {
        final JSONArray json = jsonData.getJSONArray("breakpoints");
        final List<FunctionBreakpoint> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new FunctionBreakpoint(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public SetFunctionBreakpointsArguments setBreakpoints(List<FunctionBreakpoint> breakpoints) {
        final JSONArray json = new JSONArray();
        for (FunctionBreakpoint functionBreakpoint : breakpoints) {
            json.put(functionBreakpoint.jsonData);
        }
        jsonData.put("breakpoints", json);
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
        SetFunctionBreakpointsArguments other = (SetFunctionBreakpointsArguments) obj;
        if (!Objects.equals(this.getBreakpoints(), other.getBreakpoints())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.getBreakpoints());
        return hash;
    }

    public static SetFunctionBreakpointsArguments create(List<FunctionBreakpoint> breakpoints) {
        final JSONObject json = new JSONObject();
        JSONArray breakpointsJsonArr = new JSONArray();
        for (FunctionBreakpoint functionBreakpoint : breakpoints) {
            breakpointsJsonArr.put(functionBreakpoint.jsonData);
        }
        json.put("breakpoints", breakpointsJsonArr);
        return new SetFunctionBreakpointsArguments(json);
    }
}
