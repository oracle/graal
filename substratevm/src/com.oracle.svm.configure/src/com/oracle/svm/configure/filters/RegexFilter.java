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

import static com.oracle.svm.core.configure.ConfigurationParser.asList;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.oracle.svm.configure.json.JsonWriter;

public class RegexFilter implements ConfigurationFilter {

    private final Map<Inclusion, Map<String, Pattern>> regexPatterns;

    public RegexFilter() {
        regexPatterns = new HashMap<>();
        for (Inclusion inclusion : Inclusion.values()) {
            regexPatterns.put(inclusion, new HashMap<>());
        }
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.quote("regexRules").append(": [").indent().newline();

        boolean first = true;
        for (Inclusion inclusion : Inclusion.values()) {
            for (String pattern : regexPatterns.get(inclusion).keySet()) {
                if (first) {
                    first = false;
                } else {
                    writer.append(',').newline();
                }
                writer.append("{").quote(inclusion == Inclusion.Include ? "includeClasses" : "excludeClasses").append(": ").quote(pattern).append("}");
            }
        }

        writer.unindent().newline();
        writer.append("]");
        writer.unindent().newline();
        writer.append("}");
    }

    @Override
    public void parseFromJson(Map<String, Object> topJsonObject) {
        Object regexRules = topJsonObject.get("regexRules");
        if (regexRules != null) {
            List<Object> patternList = asList(regexRules, "Field 'regexRules' must be a list of objects.");
            for (Object patternObject : patternList) {
                RuleNode.parseEntry(patternObject, (pattern, inclusion) -> regexPatterns.get(inclusion).computeIfAbsent(pattern, Pattern::compile));
            }
        }
    }

    private boolean matchesForInclusion(Inclusion inclusion, String qualifiedName) {
        return regexPatterns.get(inclusion).values().stream().anyMatch(p -> p.matcher(qualifiedName).matches());
    }

    private boolean hasPatternsForInclusion(Inclusion inclusion) {
        return regexPatterns.get(inclusion).size() != 0;
    }

    @Override
    public boolean includes(String qualifiedName) {
        if (hasPatternsForInclusion(Inclusion.Include)) {
            if (!matchesForInclusion(Inclusion.Include, qualifiedName)) {
                return false;
            }
        }
        if (hasPatternsForInclusion(Inclusion.Exclude)) {
            return !matchesForInclusion(Inclusion.Exclude, qualifiedName);
        }
        return true;
    }
}
