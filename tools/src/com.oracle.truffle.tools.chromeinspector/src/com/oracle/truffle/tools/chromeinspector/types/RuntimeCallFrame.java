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

public final class RuntimeCallFrame {

    private final String functionName;
    private final int scriptId;
    private final String url;
    private final int line;
    private final int column;

    /**
     * A stack entry with 1-based line numbers and 1-based columns.
     */
    public RuntimeCallFrame(String functionName, int scriptId, String url, int line, int column) {
        this.functionName = functionName;
        this.scriptId = scriptId;
        this.url = url;
        this.line = line;
        this.column = column;
    }

    public String getFunctionName() {
        return functionName;
    }

    public int getScriptId() {
        return scriptId;
    }

    public String getUrl() {
        return url;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("functionName", functionName);
        json.put("scriptId", Integer.toString(scriptId));
        json.put("url", url);
        json.put("lineNumber", line - 1);   // 0-based in the protocol
        json.put("columnNumber", column - 1);   // 0-based in the protocol
        return json;
    }
}
