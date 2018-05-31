/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

public final class Location {

    private final int scriptId;
    private final int line;
    private final int column;

    /**
     * A location with 1-based line numbers and 1-based columns. When the column is 0, it is
     * considered to be undefined.
     */
    public Location(int scriptId, int line, int column) {
        this.scriptId = scriptId;
        this.line = line;
        this.column = column;
    }

    /**
     * Create a location from the JSON description.
     *
     * @return the location, or <code>null</code>
     */
    public static Location create(JSONObject location) {
        if (location == null) {
            return null;
        }
        String scriptId = location.optString("scriptId");
        if (scriptId == null) {
            return null;
        }
        int line = location.optInt("lineNumber", -1);
        if (line < 0) {
            return null;
        }
        int column = location.optInt("columnNumber", -1);
        try {
            return new Location(Integer.parseInt(scriptId), line + 1, column + 1);
        } catch (NumberFormatException nfex) {
            return null;
        }
    }

    public int getScriptId() {
        return scriptId;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("scriptId", Integer.toString(scriptId));
        json.put("lineNumber", line - 1);       // 0-based in the protocol
        if (column > 0) {
            json.put("columnNumber", column - 1);   // 0-based in the protocol
        }
        return json;
    }
}
