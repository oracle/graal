/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.template;

import java.util.*;
import java.util.regex.*;

public final class JavaName {

    private static final String[] RESERVED_NAMES = new String[]{"abstract", "continue", "for", "new", "switch", "assert", "default", "goto", "package", "synchronized", "boolean", "do", "if",
                    "private", "this", "break", "double", "implements", "protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum", "instanceof", "return", "transient",
                    "catch", "extends", "int", "short", "try", "char", "final", "interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const", "float", "native", "super",
                    "while"};

    private static final Set<String> RESERVED_NAMES_SET = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(RESERVED_NAMES)));

    private static final Pattern VALID_JAVA_NAME_PATTERN = Pattern.compile("[_a-zA-z][_a-zA-Z0-9]*");

    private JavaName() {
        super();
    }

    public static boolean isReserved(String name) {
        return RESERVED_NAMES_SET.contains(name);
    }

    public static boolean isValid(String typeName) {
        return VALID_JAVA_NAME_PATTERN.matcher(typeName).matches();
    }
}
