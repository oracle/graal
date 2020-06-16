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
package com.oracle.truffle.tools.chromeinspector.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.chromeinspector.util.LineSearch;

import com.oracle.truffle.tools.utils.json.JSONArray;

@RunWith(Parameterized.class)
public class LineSearchTest {

    private static final String CONTENT_1LINE = "abcdef ghijacef";
    private static final String CONTENT_MULTI_LINE = "\na\nb\ncdef g\n\n\nhijacef\n";

    // EOL strings in addition to "\n"
    private static final String[] EOL_STRINGS = new String[]{"\r", "\r\n"};

    @Parameterized.Parameters(name = "{0}, {1}, {2}, {3}")
    @SuppressWarnings("unchecked")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();

        ret.add(new Object[]{CONTENT_1LINE, "a", false, false, true, result(0, CONTENT_1LINE)});
        ret.add(new Object[]{CONTENT_1LINE, "Cef", false, false, true, result(0, CONTENT_1LINE)});
        ret.add(new Object[]{CONTENT_1LINE, "A", true, false, true, result()});
        ret.add(new Object[]{CONTENT_1LINE.toUpperCase(), "A", true, false, true, result(0, CONTENT_1LINE.toUpperCase())});
        ret.add(new Object[]{CONTENT_1LINE, "f", false, false, true, result(0, CONTENT_1LINE)});
        ret.add(new Object[]{CONTENT_1LINE, "gg", true, false, true, result()});
        ret.add(new Object[]{CONTENT_1LINE, CONTENT_1LINE, true, false, true, result(0, CONTENT_1LINE)});

        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "bc", false, false, true, result()}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "A", false, false, true, result(1, "a", 6, "hijacef")}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "Ef", false, false, true, result(3, "cdef g", 6, "hijacef")}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "hi", true, false, true, result(6, "hijacef")}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "HI", true, false, true, result()}));

        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "bc", false, true, true, result()}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "C", false, true, true, result(3, "cdef g", 6, "hijacef")}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "H[A-Z]*E", false, true, true, result(6, "hijacef")}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "H[A-Z]*E", true, true, true, result()}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "h[a-z]*e", true, true, true, result(6, "hijacef")}));

        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "H)(A-Z]E", true, true, false, result()}));
        ret.addAll(generateDifferentNewLines(new Object[]{CONTENT_MULTI_LINE, "H$^@][*E", true, true, false, result()}));

        return ret;
    }

    private static String result(Object... lines) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < lines.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{\"lineNumber\":");
            builder.append(lines[i]);
            builder.append(",\"lineContent\":\"");
            builder.append(lines[i + 1]);
            builder.append("\"}");
        }
        builder.append(']');
        return builder.toString();
    }

    private static Collection<Object[]> generateDifferentNewLines(Object[] params) {
        List<Object[]> diffNL = new ArrayList<>(EOL_STRINGS.length + 1);
        diffNL.add(params);
        for (String eol : EOL_STRINGS) {
            Object[] params2 = params.clone();
            params2[0] = ((String) params2[0]).replace("\n", eol);
            diffNL.add(params2);
        }
        // Combination of newline characters:
        Object[] params2 = params.clone();
        StringBuilder text = new StringBuilder((String) params2[0]);
        int nlIndex = -1;
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\n') {
                if (nlIndex >= 0) {
                    String nlReplace = EOL_STRINGS[nlIndex];
                    text.replace(i, i + 1, nlReplace);
                    i += nlReplace.length() - 1;
                }
                nlIndex++;
                if (nlIndex >= EOL_STRINGS.length) {
                    nlIndex = -1;
                }
            }
            i++;
        }
        params2[0] = text.toString();
        diffNL.add(params2);
        return diffNL;
    }

    private final CharSequence content;
    private final String query;
    private final boolean caseSensitive;
    private final boolean isRegex;
    private final boolean isValid;
    private final String result;

    public LineSearchTest(CharSequence content, String query, boolean caseSensitive, boolean isRegex, boolean isValid, String result) {
        this.content = content;
        this.query = query;
        this.caseSensitive = caseSensitive;
        this.isRegex = isRegex;
        this.isValid = isValid;
        this.result = result;
    }

    @Test
    public void testSearch() {
        JSONArray matchLines;
        Source source = Source.newBuilder("test", content, null).build();
        try {
            matchLines = LineSearch.matchLines(source, query, caseSensitive, isRegex);
            Assert.assertTrue("Succeeded despite invalid query " + query, isValid);
        } catch (PatternSyntaxException ex) {
            if (isValid) {
                throw ex;
            } else {
                // Failed as expected
                return;
            }
        }
        Assert.assertEquals(result, matchLines.toString());
    }
}
