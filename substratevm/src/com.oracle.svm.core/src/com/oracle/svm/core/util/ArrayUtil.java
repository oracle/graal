/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.lang.reflect.Array;

import org.graalvm.compiler.nodes.java.ArrayLengthNode;

public class ArrayUtil {
    public static boolean isOutOfBounds(Object array, int start, int count) {
        return start < 0 || count < 0 || start > Array.getLength(array) - count;
    }

    /**
     * Should only be used from snippets because this code uses {@link ArrayLengthNode#arrayLength},
     * which doesn't check if the object is really an array.
     */
    public static void boundsCheckInSnippet(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        if (fromIndex < 0 || toIndex < 0 || length < 0 || fromIndex > ArrayLengthNode.arrayLength(fromArray) - length || toIndex > ArrayLengthNode.arrayLength(toArray) - length) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }
}
