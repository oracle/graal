/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;
import com.oracle.svm.core.log.Log;

/**
 * Functionality to lookup and query information about units of compiled code, each represented by a
 * {@link CodeInfoHandle}.
 */
public interface CodeInfoAccessor {
    /**
     * For the unit of compiled code at the given instruction pointer, provides a handle that can be
     * used for further queries.
     */
    CodeInfoHandle lookupCodeInfo(CodePointer ip);

    boolean isNone(CodeInfoHandle handle);

    CodePointer getCodeStart(CodeInfoHandle handle);

    UnsignedWord getCodeSize(CodeInfoHandle handle);

    boolean contains(CodeInfoHandle handle, CodePointer ip);

    long relativeIP(CodeInfoHandle handle, CodePointer ip);

    CodePointer absoluteIP(CodeInfoHandle handle, long relativeIP);

    long initFrameInfoReader(CodeInfoHandle handle, CodePointer ip, ReusableTypeReader frameInfoReader);

    FrameInfoQueryResult nextFrameInfo(CodeInfoHandle handle, long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoDecoder.FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator,
                    boolean fetchFirstFrame);

    String getName(CodeInfoHandle handle);

    long lookupDeoptimizationEntrypoint(CodeInfoHandle handle, long method, long encodedBci, CodeInfoQueryResult codeInfo);

    long lookupTotalFrameSize(CodeInfoHandle handle, long ip);

    long lookupExceptionOffset(CodeInfoHandle handle, long ip);

    NonmovableArray<Byte> getReferenceMapEncoding(CodeInfoHandle handle);

    long lookupReferenceMapIndex(CodeInfoHandle handle, long relativeIP);

    void lookupCodeInfo(CodeInfoHandle handle, long ip, CodeInfoQueryResult codeInfo);

    void setFrameInfo(CodeInfoHandle installTarget, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants,
                    NonmovableObjectArray<Class<?>> sourceClasses, NonmovableObjectArray<String> sourceMethodNames, NonmovableObjectArray<String> names);

    void setCodeInfo(CodeInfoHandle installTarget, NonmovableArray<Byte> index, NonmovableArray<Byte> encodings, NonmovableArray<Byte> referenceMapEncoding);

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    Log log(CodeInfoHandle handle, Log log);

    static boolean contains(CodePointer codeStart, UnsignedWord codeSize, CodePointer ip) {
        return ((UnsignedWord) ip).subtract((UnsignedWord) codeStart).belowThan(codeSize);
    }

    static long relativeIP(CodePointer codeStart, UnsignedWord codeSize, CodePointer ip) {
        assert contains(codeStart, codeSize, ip);
        return ((UnsignedWord) ip).subtract((UnsignedWord) codeStart).rawValue();
    }

    static CodePointer absoluteIP(CodePointer codeStart, long relativeIP) {
        return (CodePointer) ((UnsignedWord) codeStart).add(WordFactory.unsigned(relativeIP));
    }

    static long initFrameInfoReader(NonmovableArray<Byte> codeInfoEncodings, NonmovableArray<Byte> codeInfoIndex,
                    NonmovableArray<Byte> frameInfoEncodings, long relativeIp, ReusableTypeReader frameInfoReader) {
        long entryOffset = CodeInfoDecoder.lookupCodeInfoEntryOffset(codeInfoIndex, codeInfoEncodings, relativeIp);
        if (entryOffset >= 0) {
            if (!CodeInfoDecoder.initFrameInfoReader(codeInfoEncodings, frameInfoEncodings, entryOffset, frameInfoReader)) {
                return -1;
            }
        }
        return entryOffset;
    }

    static FrameInfoQueryResult nextFrameInfo(NonmovableArray<Byte> codeInfoEncodings, NonmovableObjectArray<String> frameInfoNames,
                    NonmovableObjectArray<Object> frameInfoObjectConstants, NonmovableObjectArray<Class<?>> frameInfoSourceClasses,
                    NonmovableObjectArray<String> frameInfoSourceMethodNames, long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoDecoder.FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator, boolean fetchFirstFrame) {

        int entryFlags = CodeInfoDecoder.loadEntryFlags(codeInfoEncodings, entryOffset);
        boolean isDeoptEntry = CodeInfoDecoder.extractFI(entryFlags) == CodeInfoDecoder.FI_DEOPT_ENTRY_INDEX_S4;
        return FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, frameInfoReader, frameInfoObjectConstants, frameInfoSourceClasses,
                        frameInfoSourceMethodNames, frameInfoNames, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }
}
