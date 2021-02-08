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
 * Arguments for 'setBreakpoints' request.
 */
public class SetBreakpointsArguments extends JSONBase {

    SetBreakpointsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The source location of the breakpoints; either 'source.path' or 'source.reference' must be
     * specified.
     */
    public Source getSource() {
        return new Source(jsonData.getJSONObject("source"));
    }

    public SetBreakpointsArguments setSource(Source source) {
        jsonData.put("source", source.jsonData);
        return this;
    }

    /**
     * The code locations of the breakpoints.
     */
    public List<SourceBreakpoint> getBreakpoints() {
        final JSONArray json = jsonData.optJSONArray("breakpoints");
        if (json == null) {
            return null;
        }
        final List<SourceBreakpoint> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new SourceBreakpoint(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public SetBreakpointsArguments setBreakpoints(List<SourceBreakpoint> breakpoints) {
        if (breakpoints != null) {
            final JSONArray json = new JSONArray();
            for (SourceBreakpoint sourceBreakpoint : breakpoints) {
                json.put(sourceBreakpoint.jsonData);
            }
            jsonData.put("breakpoints", json);
        }
        return this;
    }

    /**
     * Deprecated: The code locations of the breakpoints.
     */
    public List<Integer> getLines() {
        final JSONArray json = jsonData.optJSONArray("lines");
        if (json == null) {
            return null;
        }
        final List<Integer> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getInt(i));
        }
        return Collections.unmodifiableList(list);
    }

    public SetBreakpointsArguments setLines(List<Integer> lines) {
        if (lines != null) {
            final JSONArray json = new JSONArray();
            for (int intValue : lines) {
                json.put(intValue);
            }
            jsonData.put("lines", json);
        }
        return this;
    }

    /**
     * A value of true indicates that the underlying source has been modified which results in new
     * breakpoint locations.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getSourceModified() {
        return jsonData.has("sourceModified") ? jsonData.getBoolean("sourceModified") : null;
    }

    public SetBreakpointsArguments setSourceModified(Boolean sourceModified) {
        jsonData.putOpt("sourceModified", sourceModified);
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
        SetBreakpointsArguments other = (SetBreakpointsArguments) obj;
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (!Objects.equals(this.getBreakpoints(), other.getBreakpoints())) {
            return false;
        }
        if (!Objects.equals(this.getLines(), other.getLines())) {
            return false;
        }
        if (!Objects.equals(this.getSourceModified(), other.getSourceModified())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + Objects.hashCode(this.getSource());
        if (this.getBreakpoints() != null) {
            hash = 59 * hash + Objects.hashCode(this.getBreakpoints());
        }
        if (this.getLines() != null) {
            hash = 59 * hash + Objects.hashCode(this.getLines());
        }
        if (this.getSourceModified() != null) {
            hash = 59 * hash + Boolean.hashCode(this.getSourceModified());
        }
        return hash;
    }

    public static SetBreakpointsArguments create(Source source) {
        final JSONObject json = new JSONObject();
        json.put("source", source.jsonData);
        return new SetBreakpointsArguments(json);
    }
}
