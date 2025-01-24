/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jvmti.headers.JvmtiInterface;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.api.replacements.Fold;

public final class JvmtiFunctionTable {
    /**
     * A table with function pointers to all JVMTI entry points (see {@link JvmtiFunctions}). This
     * table is filled at image-build-time.
     */
    private final CFunctionPointer[] readOnlyFunctionTable;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiFunctionTable() {
        int length = bytesToWords(SizeOf.get(JvmtiInterface.class));
        readOnlyFunctionTable = new CFunctionPointer[length];
    }

    @Fold
    public static JvmtiFunctionTable singleton() {
        return ImageSingletons.lookup(JvmtiFunctionTable.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void init(int offsetInBytes, CFunctionPointer functionPointer) {
        int index = bytesToWords(offsetInBytes);
        assert readOnlyFunctionTable[index] == null;
        readOnlyFunctionTable[index] = functionPointer;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int bytesToWords(int bytes) {
        assert bytes % ConfigurationValues.getTarget().wordSize == 0;
        return bytes / ConfigurationValues.getTarget().wordSize;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public CFunctionPointer[] getReadOnlyFunctionTable() {
        return readOnlyFunctionTable;
    }

    public static JvmtiInterface allocateFunctionTable() {
        UnsignedWord size = SizeOf.unsigned(JvmtiInterface.class);
        JvmtiInterface result = NullableNativeMemory.malloc(size, NmtCategory.JVMTI);
        if (result.isNonNull()) {
            NonmovableArray<?> readOnlyData = NonmovableArrays.fromImageHeap(singleton().readOnlyFunctionTable);
            assert size.equal(NonmovableArrays.lengthOf(readOnlyData) * ConfigurationValues.getTarget().wordSize);
            UnmanagedMemoryUtil.copyForward(NonmovableArrays.getArrayBase(readOnlyData), (Pointer) result, size);
        }
        return result;
    }

    public static void freeFunctionTable(JvmtiInterface table) {
        NullableNativeMemory.free(table);
    }
}
