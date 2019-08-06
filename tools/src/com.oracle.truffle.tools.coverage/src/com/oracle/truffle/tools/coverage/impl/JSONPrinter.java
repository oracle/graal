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

import java.io.PrintStream;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

class JSONPrinter {

    JSONPrinter(PrintStream out, SourceCoverage[] coverage) {
        this.out = out;
        this.coverage = coverage;
    }

    void print() {
        JSONArray output = new JSONArray();
        for (SourceCoverage sourceCoverage : coverage) {
            output.put(sourceJSON(sourceCoverage));
        }
        out.println(output.toString());
    }

    private final PrintStream out;

    private final SourceCoverage[] coverage;

    private static JSONObject sourceSectionJson(SourceSection section) {
        JSONObject sourceSection = new JSONObject();
        sourceSection.put("characters", section.getCharacters());
        sourceSection.put("start_line", section.getStartLine());
        sourceSection.put("end_line", section.getEndLine());
        sourceSection.put("start_column", section.getStartColumn());
        sourceSection.put("end_column", section.getEndColumn());
        sourceSection.put("char_index", section.getCharIndex());
        sourceSection.put("char_end_index", section.getCharEndIndex());
        sourceSection.put("char_length", section.getCharLength());
        return sourceSection;
    }

    private JSONObject sourceJSON(SourceCoverage coverage) {
        final JSONObject sourceJson = new JSONObject();
        sourceJson.put("path", coverage.getSource().getPath());
        sourceJson.put("roots", rootsJson(coverage.getRoots()));
        return sourceJson;
    }

    private JSONArray rootsJson(RootCoverage[] coverages) {
        final JSONArray rootsJson = new JSONArray();
        for (RootCoverage coverage : coverages) {
            rootsJson.put(rootJSON(coverage));
        }
        return rootsJson;
    }

    private static JSONObject rootJSON(RootCoverage coverage) {
        JSONObject rootJson = new JSONObject();
        rootJson.put("covered", coverage.isCovered());
        rootJson.put("source_section", sourceSectionJson(coverage.getSourceSection()));
        rootJson.put("name", coverage.getName());
        rootJson.put("sections", sectionsJson(coverage.getSectionCoverage()));
        return rootJson;
    }

    private static JSONArray sectionsJson(SectionCoverage[] coverages) {
        JSONArray sectionsJSON = new JSONArray();
        for (SectionCoverage coverage : coverages) {
            sectionsJSON.put(sectionJson(coverage));
        }
        return sectionsJSON;
    }

    private static JSONObject sectionJson(SectionCoverage coverage) {
        JSONObject sectionJson = new JSONObject();
        sectionJson.put("covered", coverage.isCovered());
        sectionJson.put("covered", sourceSectionJson(coverage.getSourceSection()));
        return sectionJson;
    }
}
