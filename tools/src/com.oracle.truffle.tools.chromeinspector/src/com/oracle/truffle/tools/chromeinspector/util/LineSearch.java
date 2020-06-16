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
package com.oracle.truffle.tools.chromeinspector.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

public final class LineSearch {

    private LineSearch() {
    }

    public static JSONArray matchLines(Source source, String query, final boolean caseSensitive, final boolean isRegex) throws PatternSyntaxException {
        JSONArray matchLines = new JSONArray();
        Pattern pattern = isRegex ? Pattern.compile(query, caseSensitive ? 0 : Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE) : null;
        for (int lineNumber = 1; lineNumber <= source.getLineCount(); lineNumber++) {
            String line = source.getCharacters(lineNumber).toString();
            if (isRegex) {
                assert pattern != null;
                if (pattern.matcher(line).find()) {
                    addSearchMatch(matchLines, lineNumber, line);
                }
            } else {
                if (caseSensitive && line.contains(query) || !caseSensitive && containsIgnoreCase(line, query)) {
                    addSearchMatch(matchLines, lineNumber, line);
                }
            }
        }
        return matchLines;
    }

    private static void addSearchMatch(JSONArray matchLines, int lineNumber, String line) {
        JSONObject matchLine = new JSONObject();
        matchLine.put("lineNumber", lineNumber - 1);
        matchLine.put("lineContent", line);
        matchLines.put(matchLine);
    }

    private static boolean containsIgnoreCase(String line, String query) {
        int ll = line.length();
        int ql = query.length();
        if (ll == ql) {
            return line.equalsIgnoreCase(query);
        } else if (ll > ql) {
            for (int li = 0; li <= (ll - ql); li++) {
                int k = li;
                for (int qi = 0; qi < ql; qi++, k++) {
                    if (!compareIgnoreCase(line.charAt(k), query.charAt(qi))) {
                        break;
                    }
                }
                if (k == (li + ql)) {
                    // match
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean compareIgnoreCase(char c1, char c2) {
        if (c1 == c2) {
            return true;
        }
        if (Character.toUpperCase(c1) == Character.toUpperCase(c2) || Character.toLowerCase(c1) == Character.toLowerCase(c2)) {
            return true;
        }
        return false;
    }

}
