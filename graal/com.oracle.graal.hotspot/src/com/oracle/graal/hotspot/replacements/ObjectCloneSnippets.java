/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.replacements.*;

public class ObjectCloneSnippets implements Snippets {

    public static final EnumMap<Kind, Method> arrayCloneMethods = new EnumMap<>(Kind.class);

    static {
        arrayCloneMethods.put(Kind.Byte, getCloneMethod("byteArrayClone", byte[].class));
        arrayCloneMethods.put(Kind.Char, getCloneMethod("charArrayClone", char[].class));
        arrayCloneMethods.put(Kind.Int, getCloneMethod("intArrayClone", int[].class));
        arrayCloneMethods.put(Kind.Long, getCloneMethod("longArrayClone", long[].class));
        arrayCloneMethods.put(Kind.Object, getCloneMethod("objectArrayClone", Object[].class));
    }

    private static Method getCloneMethod(String name, Class<?> param) {
        try {
            return ObjectCloneSnippets.class.getDeclaredMethod(name, param);
        } catch (SecurityException | NoSuchMethodException e) {
            throw new GraalInternalError(e);
        }
    }

    @Snippet(removeAllFrameStates = true)
    public static byte[] byteArrayClone(byte[] src) {
        byte[] result = new byte[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    @Snippet(removeAllFrameStates = true)
    public static char[] charArrayClone(char[] src) {
        char[] result = new char[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    @Snippet(removeAllFrameStates = true)
    public static int[] intArrayClone(int[] src) {
        int[] result = new int[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    @Snippet(removeAllFrameStates = true)
    public static long[] longArrayClone(long[] src) {
        long[] result = new long[src.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }

    @Snippet(removeAllFrameStates = true)
    public static Object[] objectArrayClone(Object[] src) {
        Object[] result = (Object[]) DynamicNewArrayNode.newArray(GuardingPiNode.guardingNonNull(src.getClass().getComponentType()), src.length);
        for (int i = 0; i < result.length; i++) {
            result[i] = src[i];
        }
        return result;
    }
}
