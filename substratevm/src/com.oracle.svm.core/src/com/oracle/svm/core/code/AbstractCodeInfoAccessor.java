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

import com.oracle.svm.core.c.PinnedArray;
import com.oracle.svm.core.code.FrameInfoDecoder.FrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;

public abstract class AbstractCodeInfoAccessor implements CodeInfoAccessor {

    private static AbstractCodeInfo cast(CodeInfoHandle handle) {
        return (AbstractCodeInfo) handle;
    }

    @Override
    public boolean isNone(CodeInfoHandle handle) {
        return (handle == null);
    }

    @Override
    public CodePointer getCodeStart(CodeInfoHandle handle) {
        return cast(handle).getCodeStart();
    }

    @Override
    public UnsignedWord getCodeSize(CodeInfoHandle handle) {
        return cast(handle).getCodeSize();
    }

    @Override
    public boolean contains(CodeInfoHandle handle, CodePointer ip) {
        return cast(handle).contains(ip);
    }

    @Override
    public long relativeIP(CodeInfoHandle handle, CodePointer ip) {
        return cast(handle).relativeIP(ip);
    }

    @Override
    public CodePointer absoluteIP(CodeInfoHandle handle, long relativeIP) {
        return cast(handle).absoluteIP(relativeIP);
    }

    @Override
    public long initFrameInfoReader(CodeInfoHandle handle, CodePointer ip, ReusableTypeReader frameInfoReader) {
        return cast(handle).initFrameInfoReader(ip, frameInfoReader);
    }

    @Override
    public FrameInfoQueryResult nextFrameInfo(CodeInfoHandle handle, long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator, boolean fetchFirstFrame) {

        return cast(handle).nextFrameInfo(entryOffset, frameInfoReader, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    @Override
    public String getName(CodeInfoHandle handle) {
        return cast(handle).getName();
    }

    @Override
    public long lookupDeoptimizationEntrypoint(CodeInfoHandle handle, long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return cast(handle).lookupDeoptimizationEntrypoint(method, encodedBci, codeInfo);
    }

    @Override
    public long lookupTotalFrameSize(CodeInfoHandle handle, long ip) {
        return cast(handle).lookupTotalFrameSize(ip);
    }

    @Override
    public long lookupExceptionOffset(CodeInfoHandle handle, long ip) {
        return cast(handle).lookupExceptionOffset(ip);
    }

    @Override
    public PinnedArray<Byte> getReferenceMapEncoding(CodeInfoHandle handle) {
        return cast(handle).getReferenceMapEncoding();
    }

    @Override
    public long lookupReferenceMapIndex(CodeInfoHandle handle, long ip) {
        return cast(handle).lookupReferenceMapIndex(ip);
    }

    @Override
    public void lookupCodeInfo(CodeInfoHandle handle, long ip, CodeInfoQueryResult codeInfo) {
        cast(handle).lookupCodeInfo(ip, codeInfo);
    }
}
