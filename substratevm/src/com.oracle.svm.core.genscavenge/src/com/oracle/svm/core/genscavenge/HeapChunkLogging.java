/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.core.log.Log.RIGHT_ALIGN;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.log.Log;

class HeapChunkLogging {
    private static final int MAX_CHUNKS_TO_PRINT = 64 * 1024;

    public static void logChunks(Log log, AlignedHeapChunk.AlignedHeader firstChunk, String shortSpaceName, boolean isFromSpace) {
        if (firstChunk.isNonNull()) {
            int i = 0;
            AlignedHeapChunk.AlignedHeader chunk = firstChunk;
            while (chunk.isNonNull() && i < MAX_CHUNKS_TO_PRINT) {
                Pointer bottom = AlignedHeapChunk.getObjectsStart(chunk);
                Pointer top = HeapChunk.getTopPointer(chunk);
                Pointer end = AlignedHeapChunk.getObjectsEnd(chunk);

                logChunk(log, chunk, bottom, top, end, true, shortSpaceName, isFromSpace);

                chunk = HeapChunk.getNext(chunk);
                i++;
            }
            if (chunk.isNonNull()) {
                assert i == MAX_CHUNKS_TO_PRINT;
                log.newline().string("... (truncated)");
            }
        }
    }

    public static void logChunks(Log log, UnalignedHeapChunk.UnalignedHeader firstChunk, String shortSpaceName, boolean isFromSpace) {
        if (firstChunk.isNonNull()) {
            int i = 0;
            UnalignedHeapChunk.UnalignedHeader chunk = firstChunk;
            while (chunk.isNonNull() && i < MAX_CHUNKS_TO_PRINT) {
                Pointer bottom = UnalignedHeapChunk.getObjectStart(chunk);
                Pointer top = HeapChunk.getTopPointer(chunk);
                Pointer end = UnalignedHeapChunk.getObjectEnd(chunk);

                logChunk(log, chunk, bottom, top, end, false, shortSpaceName, isFromSpace);

                chunk = HeapChunk.getNext(chunk);
                i++;
            }
            if (chunk.isNonNull()) {
                assert i == MAX_CHUNKS_TO_PRINT;
                log.newline().string("... (truncated)");
            }
        }
    }

    private static void logChunk(Log log, HeapChunk.Header<?> chunk, Pointer bottom, Pointer top, Pointer end, boolean isAligned, String shortSpaceName, boolean isFromSpace) {
        UnsignedWord used = top.subtract(bottom);
        UnsignedWord capacity = end.subtract(bottom);
        UnsignedWord usedPercent = used.multiply(100).unsignedDivide(capacity);

        log.string("|").zhex(chunk).string("|").zhex(bottom).string(", ").zhex(top).string(", ").zhex(end);
        log.string("|").unsigned(usedPercent, 3, RIGHT_ALIGN).string("%");
        log.string("|").string(shortSpaceName, 3, RIGHT_ALIGN);
        log.string("|").string(isAligned ? "A" : "U");
        log.string("|").string(isFromSpace ? "" : "T");
        log.newline();
    }
}
