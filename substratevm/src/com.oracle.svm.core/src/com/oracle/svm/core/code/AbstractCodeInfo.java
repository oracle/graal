/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.code.FrameInfoDecoder.FrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;

/**
 * Information about a block of memory that contains machine code.
 *
 * This class extends {@link CodeInfoDecoder} instead of using it. This eliminates following one
 * pointer when accessing the information, which happens very frequently during GC and exception
 * handling.
 */
public abstract class AbstractCodeInfo extends CodeInfoDecoder {

    @UnknownPrimitiveField private CodePointer codeStart;
    @UnknownPrimitiveField private UnsignedWord codeSize;

    protected void setData(CodePointer codeStart, UnsignedWord codeSize) {
        this.codeStart = codeStart;
        this.codeSize = codeSize;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    public CodePointer getCodeStart() {
        return codeStart;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    public UnsignedWord getCodeSize() {
        return codeSize;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    protected CodePointer getCodeEnd() {
        return (CodePointer) ((UnsignedWord) codeStart).add(codeSize);
    }

    public boolean contains(CodePointer ip) {
        return ((UnsignedWord) ip).subtract((UnsignedWord) codeStart).belowThan(codeSize);
    }

    public long relativeIP(CodePointer ip) {
        assert contains(ip);
        return ((UnsignedWord) ip).subtract((UnsignedWord) codeStart).rawValue();
    }

    public CodePointer absoluteIP(long relativeIP) {
        return (CodePointer) ((UnsignedWord) codeStart).add(WordFactory.unsigned(relativeIP));
    }

    public long initFrameInfoReader(CodePointer ip, ReusableTypeReader frameInfoReader) {
        long entryOffset = lookupCodeInfoEntryOffset(relativeIP(ip));
        if (entryOffset >= 0) {
            if (!initFrameInfoReader(entryOffset, frameInfoReader)) {
                return -1;
            }
        }
        return entryOffset;
    }

    public FrameInfoQueryResult nextFrameInfo(long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator,
                    boolean fetchFirstFrame) {
        int entryFlags = loadEntryFlags(entryOffset);
        boolean isDeoptEntry = extractFI(entryFlags) == FI_DEOPT_ENTRY_INDEX_S4;
        return FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, frameInfoReader, frameInfoObjectConstants, frameInfoSourceClassNames,
                        frameInfoSourceMethodNames, frameInfoSourceFileNames, frameInfoNames, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    public abstract String getName();
}
