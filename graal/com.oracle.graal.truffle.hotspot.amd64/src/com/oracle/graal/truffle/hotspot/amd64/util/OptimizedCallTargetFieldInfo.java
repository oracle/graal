/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot.amd64.util;

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.truffle.*;

public class OptimizedCallTargetFieldInfo {

    private static final Unsafe unsafe = UnsafeAccess.unsafe;
    private static int compiledMethodFieldOffset = -1;
    private static int codeBlobFieldOffset = -1;

    public static int getCodeBlobFieldOffset() {
        if (codeBlobFieldOffset == -1) {
            codeBlobFieldOffset = getFieldOffset("codeBlob", HotSpotInstalledCode.class);
        }
        return codeBlobFieldOffset;
    }

    public static int getCompiledMethodFieldOffset() {
        if (compiledMethodFieldOffset == -1) {
            compiledMethodFieldOffset = getFieldOffset("compiledMethod", OptimizedCallTarget.class);
        }
        return compiledMethodFieldOffset;

    }

    private static int getFieldOffset(String name, Class container) {
        try {
            container.getDeclaredField(name).setAccessible(true);
            Field field = container.getDeclaredField(name);
            return (int) unsafe.objectFieldOffset(field);
        } catch (NoSuchFieldException | SecurityException e) {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
