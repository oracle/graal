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

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

public final class FunctionCoverage {

    private final String functionName;
    private final boolean isBlockCoverage;
    private final CoverageRange[] ranges;

    public FunctionCoverage(String functionName, boolean isBlockCoverage, CoverageRange... ranges) {
        this.functionName = functionName;
        this.isBlockCoverage = isBlockCoverage;
        this.ranges = ranges;
    }

    public String getFunctionName() {
        return functionName;
    }

    public boolean isBlockCoverage() {
        return isBlockCoverage;
    }

    public CoverageRange[] getRanges() {
        return ranges;
    }

    private JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("functionName", functionName);
        json.put("ranges", CoverageRange.toJSON(ranges));
        json.put("isBlockCoverage", isBlockCoverage);
        return json;
    }

    static JSONArray toJSON(FunctionCoverage[] functions) {
        JSONArray array = new JSONArray();
        for (FunctionCoverage function : functions) {
            array.put(function.toJSON());
        }
        return array;
    }
}
