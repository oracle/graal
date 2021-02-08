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
 * Arguments for 'gotoTargets' request.
 */
public class GotoTargetsArguments extends JSONBase {

    GotoTargetsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The source location for which the goto targets are determined.
     */
    public Source getSource() {
        return new Source(jsonData.getJSONObject("source"));
    }

    public GotoTargetsArguments setSource(Source source) {
        jsonData.put("source", source.jsonData);
        return this;
    }

    /**
     * The line location for which the goto targets are determined.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public GotoTargetsArguments setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * An optional column location for which the goto targets are determined.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public GotoTargetsArguments setColumn(Integer column) {
        jsonData.putOpt("column", column);
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
        GotoTargetsArguments other = (GotoTargetsArguments) obj;
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (this.getLine() != other.getLine()) {
            return false;
        }
        if (!Objects.equals(this.getColumn(), other.getColumn())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.getSource());
        hash = 59 * hash + Integer.hashCode(this.getLine());
        if (this.getColumn() != null) {
            hash = 59 * hash + Integer.hashCode(this.getColumn());
        }
        return hash;
    }

    public static GotoTargetsArguments create(Source source, Integer line) {
        final JSONObject json = new JSONObject();
        json.put("source", source.jsonData);
        json.put("line", line);
        return new GotoTargetsArguments(json);
    }
}
