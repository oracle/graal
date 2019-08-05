/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage.impl;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.PrintStream;

class JSONPrinter {

    JSONPrinter(PrintStream out, SourceCoverage[] coverage) {
        this.out = out;
        this.coverage = coverage;
    }

    void print() {
        JSONArray output = new JSONArray();
        for (SourceCoverage sourceCoverage: coverage) {
            output.put(sourceJSON(sourceCoverage));
        }
        out.println(output.toString());
    }

    private final PrintStream out;

    private final SourceCoverage[] coverage;

    private static JSONArray statementsJson(SourceSection[] statements) {
        final JSONArray array = new JSONArray();
        for (SourceSection statement : statements) {
            array.put(sourceSectionJson(statement));
        }
        return array;
    }

    private static JSONObject sourceSectionJson(SourceSection statement) {
        JSONObject sourceSection = new JSONObject();
        sourceSection.put("characters", statement.getCharacters());
        sourceSection.put("start_line", statement.getStartLine());
        sourceSection.put("end_line", statement.getEndLine());
        sourceSection.put("start_column", statement.getStartColumn());
        sourceSection.put("end_column", statement.getEndColumn());
        sourceSection.put("char_index", statement.getCharIndex());
        sourceSection.put("char_end_index", statement.getCharEndIndex());
        sourceSection.put("char_length", statement.getCharLength());
        return sourceSection;
    }

    private JSONObject sourceJSON(SourceCoverage sourceCoverage) {
        final JSONObject sourceJson = new JSONObject();
        sourceJson.put("path", sourceCoverage.getSource().getPath());
        final JSONArray rootsJson = new JSONArray();
        for (RootCoverage rootCoverage : sourceCoverage.getRoots()) {
            rootsJson.put(rootJSON(rootCoverage));
        }
        sourceJson.put("roots", rootsJson);
        return sourceJson;
    }

    private static JSONObject rootJSON(RootCoverage rootCoverage) {
        JSONObject rootJson = new JSONObject();
        rootJson.put("loaded_statements", statementsJson(rootCoverage.getLoadedStatements()));
        rootJson.put("covered_statements", statementsJson(rootCoverage.getCoveredStatements()));
        rootJson.put("covered", rootCoverage.isCovered());
        rootJson.put("source_section", sourceSectionJson(rootCoverage.getSourceSection()));
        rootJson.put("name", rootCoverage.getName());
        return rootJson;
    }
}
