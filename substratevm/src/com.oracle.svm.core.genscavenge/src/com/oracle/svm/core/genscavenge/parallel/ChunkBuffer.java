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

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

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

    void push(Pointer ptr) {
        assert !ParallelGC.isInParallelPhase() || ParallelGC.mutex.isOwner();
        if (top >= size) {
            int oldSize = size;
            size *= 2;
            assert top < size;
            buffer = realloc(buffer, oldSize, size);
        }
        buffer.writeWord(top, ptr);
        top += wordSize();
    }

    Pointer pop() {
        assert ParallelGC.isInParallelPhase();
        ParallelGC.mutex.lock();
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
        free(buffer, size);
    }

    @AlwaysInline("GC performance")
    private static Pointer malloc(int bytes) {
        return CommittedMemoryProvider.get().allocateUnalignedChunk(WordFactory.unsigned(bytes));
    }

    @AlwaysInline("GC performance")
    private static Pointer realloc(Pointer orig, int origSize, int newSize) {
        Pointer ptr = malloc(newSize);
        UnmanagedMemoryUtil.copyLongsForward(orig, ptr, WordFactory.unsigned(origSize));
        free(orig, origSize);
        return ptr;
    }

    @AlwaysInline("GC performance")
    private static void free(Pointer ptr, int bytes) {
        CommittedMemoryProvider.get().freeUnalignedChunk(ptr, WordFactory.unsigned(bytes));
    }
}
