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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

/**
 * Synchronized buffer that stores pointers into "grey" heap chunks that need to be scanned. Note
 * that the pointers don't necessarily point to the beginning of a chunk.
 */
public class ChunkBuffer {
    private static final int INITIAL_SIZE = 1024 * wordSize();

    private Pointer buffer;
    private int size = INITIAL_SIZE;
    private int top;

    @Fold
    static int wordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    ChunkBuffer() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void initialize() {
        buffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(size));
        VMError.guarantee(buffer.isNonNull());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void push(Pointer ptr) {
        assert !ParallelGC.singleton().isInParallelPhase() && VMThreads.ownsThreadMutex() || ParallelGC.singleton().getMutex().hasOwner();
        if (top >= size) {
            size *= 2;
            assert top < size;
            buffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(buffer, WordFactory.unsigned(size));
            VMError.guarantee(buffer.isNonNull());
        }
        buffer.writeWord(top, ptr);
        top += wordSize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Pointer pop() {
        assert ParallelGC.singleton().isInParallelPhase();
        ParallelGC.singleton().getMutex().lockNoTransitionUnspecifiedOwner();
        try {
            if (top > 0) {
                top -= wordSize();
                return buffer.readWord(top);
            } else {
                return WordFactory.nullPointer();
            }
        } finally {
            ParallelGC.singleton().getMutex().unlockNoTransitionUnspecifiedOwner();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isEmpty() {
        assert !ParallelGC.singleton().isInParallelPhase();
        return top == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void release() {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(buffer);
    }
}
