/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, BELLSOFT. All rights reserved.
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

package com.oracle.svm.core.genscavenge.parallel;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;

/**
 * Synchronized buffer that stores "grey" heap chunks to be scanned.
 */
public class ChunkBuffer {
    private static final int INITIAL_SIZE = 1024 * wordSize();

    private Pointer buffer;
    private int size;
    private int top;

    @Fold
    static int wordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }

    ChunkBuffer() {
        this.size = INITIAL_SIZE;
        this.buffer = malloc(this.size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void push(Pointer ptr) {
        assert !ParallelGC.isInParallelPhase() || ParallelGC.mutex.hasOwner();
        if (top >= size) {
            int oldSize = size;
            size *= 2;
            assert top < size;
            buffer = realloc(buffer, size);
        }
        buffer.writeWord(top, ptr);
        top += wordSize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Pointer pop() {
        assert ParallelGC.isInParallelPhase();
        ParallelGC.mutex.lockNoTransitionUnspecifiedOwner();
        try {
            if (top > 0) {
                top -= wordSize();
                return buffer.readWord(top);
            } else {
                return WordFactory.nullPointer();
            }
        } finally {
            ParallelGC.mutex.unlock();
        }
    }

    boolean isEmpty() {
        assert !ParallelGC.isInParallelPhase();
        return top == 0;
    }

    void release() {
        free(buffer);
    }

    private static Pointer malloc(int bytes) {
        // TEMP (chaeubl): needs proper error handling
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(bytes));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer realloc(Pointer orig, int newSize) {
        // TEMP (chaeubl): needs proper error handling
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(orig, WordFactory.unsigned(newSize));
    }

    private static void free(Pointer ptr) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(ptr);
    }
}
