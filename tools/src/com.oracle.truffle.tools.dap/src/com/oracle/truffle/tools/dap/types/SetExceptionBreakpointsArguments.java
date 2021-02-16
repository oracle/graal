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
 * Arguments for 'setExceptionBreakpoints' request.
 */
public class SetExceptionBreakpointsArguments extends JSONBase {

    SetExceptionBreakpointsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * IDs of checked exception options. The set of IDs is returned via the
     * 'exceptionBreakpointFilters' capability.
     */
    public List<String> getFilters() {
        final JSONArray json = jsonData.getJSONArray("filters");
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public SetExceptionBreakpointsArguments setFilters(List<String> filters) {
        final JSONArray json = new JSONArray();
        for (String string : filters) {
            json.put(string);
        }
        jsonData.put("filters", json);
        return this;
    }

    /**
     * Configuration options for selected exceptions. The attribute is only honored by a debug
     * adapter if the capability 'supportsExceptionOptions' is true.
     */
    public List<ExceptionOptions> getExceptionOptions() {
        final JSONArray json = jsonData.optJSONArray("exceptionOptions");
        if (json == null) {
            return null;
        }
        final List<ExceptionOptions> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new ExceptionOptions(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public SetExceptionBreakpointsArguments setExceptionOptions(List<ExceptionOptions> exceptionOptions) {
        if (exceptionOptions != null) {
            final JSONArray json = new JSONArray();
            for (ExceptionOptions options : exceptionOptions) {
                json.put(options.jsonData);
            }
            jsonData.put("exceptionOptions", json);
        }
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
        SetExceptionBreakpointsArguments other = (SetExceptionBreakpointsArguments) obj;
        if (!Objects.equals(this.getFilters(), other.getFilters())) {
            return false;
        }
        if (!Objects.equals(this.getExceptionOptions(), other.getExceptionOptions())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.getFilters());
        if (this.getExceptionOptions() != null) {
            hash = 61 * hash + Objects.hashCode(this.getExceptionOptions());
        }
        return hash;
    }

    public static SetExceptionBreakpointsArguments create(List<String> filters) {
        final JSONObject json = new JSONObject();
        JSONArray filtersJsonArr = new JSONArray();
        for (String string : filters) {
            filtersJsonArr.put(string);
        }
        json.put("filters", filtersJsonArr);
        return new SetExceptionBreakpointsArguments(json);
    }
}
