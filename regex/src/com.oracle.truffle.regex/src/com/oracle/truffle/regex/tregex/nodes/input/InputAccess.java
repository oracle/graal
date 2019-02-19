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
package com.oracle.truffle.regex.tregex.nodes.input;

import com.oracle.truffle.api.ArrayUtils;

/**
 * This class encapsulates all accesses to methods of the input string of a regex search operation,
 * to simplify transitions to other string representations.
 */
public final class InputAccess {

    public static char charAt(Object input, int index) {
        return ((String) input).charAt(index);
    }

    public static int length(Object input) {
        return ((String) input).length();
    }

    public static boolean startsWith(Object input, String prefix) {
        return ((String) input).startsWith(prefix);
    }

    public static boolean endsWith(Object input, String suffix) {
        return ((String) input).endsWith(suffix);
    }

    public static boolean equals(Object input, String other) {
        return ((String) input).equals(other);
    }

    public static int indexOf(Object input, int fromIndex, int maxIndex, char[] chars) {
        return ArrayUtils.indexOf((String) input, fromIndex, maxIndex, chars);
    }

    public static int indexOfString(Object input, String match, int fromIndex, int maxIndex) {
        int result = ((String) input).indexOf(match, fromIndex);
        return result >= maxIndex ? -1 : result;
    }

    public static boolean regionMatches(Object input, String match, int fromIndex) {
        return ((String) input).regionMatches(fromIndex, match, 0, match.length());
    }
}
