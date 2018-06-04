/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.functions;

import java.util.stream.IntStream;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.util.VMError;

/**
 * Initializes a function table struct at runtime by writing values to specific offsets.
 */
class JNIStructFunctionsInitializer<T extends PointerBase> {
    private final int structSize;

    private WordBase defaultValue;

    @UnknownObjectField(types = int[].class) //
    private int[] offsets;

    @UnknownObjectField(types = CFunctionPointer[].class) //
    private CFunctionPointer[] functions;

    JNIStructFunctionsInitializer(Class<T> structClass, int[] offsets, CFunctionPointer[] functions, WordBase defaultValue) {
        this.structSize = SizeOf.get(structClass);
        this.offsets = offsets;
        this.functions = functions;
        this.defaultValue = defaultValue;

        int wordSize = FrameAccess.wordSize();
        VMError.guarantee(structSize % wordSize == 0 && IntStream.of(offsets).allMatch(offset -> (offset % wordSize == 0)),
                        "Unaligned struct breaks default value initialization");
    }

    public void initialize(T structure) {
        Pointer p = (Pointer) structure;
        int wordSize = FrameAccess.wordSize();
        for (int k = 0; k + wordSize <= structSize; k += wordSize) {
            p.writeWord(k, defaultValue);
        }
        for (int i = 0; i < offsets.length; i++) {
            p.writeWord(offsets[i], functions[i]);
        }
    }
}
