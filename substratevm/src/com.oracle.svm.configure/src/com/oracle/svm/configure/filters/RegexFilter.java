/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.filters;

import static com.oracle.svm.configure.ConfigurationParser.asList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.util.json.JsonWriter;

public class RegexFilter implements ConfigurationFilter {

    private final Pattern[][] regexPatterns = new Pattern[Inclusion.values().length][];

    public RegexFilter() {
        for (Inclusion inclusion : Inclusion.values()) {
            regexPatterns[inclusion.ordinal()] = new Pattern[0];
        }
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.quote("regexRules").append(": [").indent().newline();

        boolean[] first = {true};
        for (Inclusion inclusion : Inclusion.values()) {
            for (Pattern pattern : regexPatterns[inclusion.ordinal()]) {
                FilterConfigurationParser.printEntry(writer, first, inclusion, pattern.pattern());
            }
        }

        writer.unindent().newline();
        writer.append("]");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void parseFromJson(EconomicMap<String, Object> topJsonObject) {
        Object regexRules = topJsonObject.get("regexRules");
        if (regexRules != null) {
            List<Object> patternList = asList(regexRules, "Field 'regexRules' must be a list of objects.");
            List<Pattern>[] patterns = new List[Inclusion.values().length];
            for (Inclusion inclusion : Inclusion.values()) {
                patterns[inclusion.ordinal()] = new ArrayList<>(Arrays.asList(regexPatterns[inclusion.ordinal()]));
            }

            for (Object patternObject : patternList) {
                FilterConfigurationParser.parseEntry(patternObject, (pattern, inclusion) -> patterns[inclusion.ordinal()].add(Pattern.compile(pattern)));
            }

            for (Inclusion inclusion : Inclusion.values()) {
                regexPatterns[inclusion.ordinal()] = patterns[inclusion.ordinal()].toArray(new Pattern[0]);
            }
        }
    }

    private boolean matchesForInclusion(Inclusion inclusion, String qualifiedName) {
        for (Pattern p : regexPatterns[inclusion.ordinal()]) {
            if (p.matcher(qualifiedName).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean includes(String qualifiedName) {
        if (regexPatterns[Inclusion.Include.ordinal()].length != 0) {
            if (!matchesForInclusion(Inclusion.Include, qualifiedName)) {
                return false;
            }
        }
        if (regexPatterns[Inclusion.Exclude.ordinal()].length != 0) {
            return !matchesForInclusion(Inclusion.Exclude, qualifiedName);
        }
        return true;
    }
}
