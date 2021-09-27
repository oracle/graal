/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.processor.builders;

import java.util.ArrayList;
import java.util.List;

public final class JavadocBuilder extends AbstractCodeBuilder {
    public static String JAVADOC_START = "/**" + NEWLINE;
    public static String JAVADOC_MIDDLE = " * ";
    public static String JAVADOC_END = " */" + NEWLINE;

    public static String link(Object o) {
        return "{@link " + o.toString() + "}";
    }

    private final List<String> lines = new ArrayList<>();

    public JavadocBuilder addLine(String... parts) {
        lines.add(String.join(" ", parts));
        return this;
    }

    @Override
    String build() {
        StringBuilder sb = new StringBuilder(JAVADOC_START);
        for (String line : lines) {
            sb.append(JAVADOC_MIDDLE).append(line).append(NEWLINE);
        }
        sb.append(JAVADOC_END);
        return sb.toString();
    }
}
