/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.util.Comparator;

import com.oracle.svm.configure.json.JsonWriter;

public class ReflectionMemberSet {
    private MatchSet<String> fields;
    private MatchSet<ReflectionMethod> methods;
    private MatchSet<ReflectionMethod> constructors;

    public MatchSet<String> getFields() {
        if (fields == null) {
            fields = MatchSet.create(Comparator.naturalOrder(), (String s, JsonWriter w) -> w.append('{').quote("name").append(':').quote(s).append('}'));
        }
        return fields;
    }

    public MatchSet<ReflectionMethod> getMethods() {
        if (methods == null) {
            methods = createMethodSet();
        }
        return methods;
    }

    public MatchSet<ReflectionMethod> getConstructors() {
        if (constructors == null) {
            constructors = createMethodSet();
        }
        return constructors;
    }

    private static MatchSet<ReflectionMethod> createMethodSet() {
        return MatchSet.create(Comparator.comparing(ReflectionMethod::getName).thenComparingInt(m -> m.getParameterTypes().length)
                        .thenComparing(m -> String.join("|", m.getParameterTypes())));
    }

    public MatchSet<String> getIndividualFields() {
        return (fields != null && !fields.matchesAll()) ? fields : null;
    }

    public MatchSet<ReflectionMethod> getIndividualMethodsAndConstructors() {
        MatchSet<ReflectionMethod> set = null;
        if (methods != null && !methods.matchesAll()) {
            set = methods;
        }
        if (constructors != null && !constructors.matchesAll()) {
            set = MatchSet.union(set, constructors);
        }
        return set;
    }

    public void printJson(JsonWriter writer, String infix) throws IOException {
        if (fields != null && fields.matchesAll()) {
            writer.append(',').newline().quote("all" + infix + "Fields").append(":true");
        }
        if (methods != null && methods.matchesAll()) {
            writer.append(',').newline().quote("all" + infix + "Methods").append(":true");
        }
        if (constructors != null && constructors.matchesAll()) {
            writer.append(',').newline().quote("all" + infix + "Constructors").append(":true");
        }
    }
}
