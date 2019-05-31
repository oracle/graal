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

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.c.PinnedArray;
import com.oracle.svm.core.c.PinnedArrays;
import com.oracle.svm.core.c.PinnedObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.FrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;

/**
 * Information about a block of memory that contains machine code.
 */
public abstract class AbstractCodeInfo implements CodeInfoHandle {
    @UnknownPrimitiveField private CodePointer codeStart;
    @UnknownPrimitiveField private UnsignedWord codeSize;

    @UnknownObjectField(types = {byte[].class}) protected byte[] codeInfoIndex;
    @UnknownObjectField(types = {byte[].class}) protected byte[] codeInfoEncodings;
    @UnknownObjectField(types = {byte[].class}) protected byte[] referenceMapEncoding;
    @UnknownObjectField(types = {byte[].class}) protected byte[] frameInfoEncodings;
    @UnknownObjectField(types = {Object[].class}) protected Object[] frameInfoObjectConstants;
    @UnknownObjectField(types = {Class[].class}) protected Class<?>[] frameInfoSourceClasses;
    @UnknownObjectField(types = {String[].class}) protected String[] frameInfoSourceMethodNames;
    @UnknownObjectField(types = {String[].class}) protected String[] frameInfoNames;

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
        return CodeInfoAccessor.contains(codeStart, codeSize, ip);
    }

    public long relativeIP(CodePointer ip) {
        return CodeInfoAccessor.relativeIP(codeStart, codeSize, ip);
    }

    public CodePointer absoluteIP(long relativeIP) {
        return CodeInfoAccessor.absoluteIP(codeStart, relativeIP);
    }

    public long initFrameInfoReader(CodePointer ip, ReusableTypeReader frameInfoReader) {
        return CodeInfoAccessor.initFrameInfoReader(pa(codeInfoEncodings), pa(codeInfoIndex), pa(frameInfoEncodings), relativeIP(ip), frameInfoReader);
    }

    public FrameInfoQueryResult nextFrameInfo(long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator,
                    boolean fetchFirstFrame) {
        return CodeInfoAccessor.nextFrameInfo(pa(codeInfoEncodings), pa(frameInfoNames), pa(frameInfoObjectConstants), pa(frameInfoSourceClasses),
                        pa(frameInfoSourceMethodNames), entryOffset, frameInfoReader, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    public abstract String getName();

    protected void setData(byte[] codeInfoIndex, byte[] codeInfoEncodings, byte[] referenceMapEncoding, byte[] frameInfoEncodings, Object[] frameInfoObjectConstants,
                    Class<?>[] frameInfoSourceClasses, String[] frameInfoSourceMethodNames, String[] frameInfoNames) {
        this.codeInfoIndex = codeInfoIndex;
        this.codeInfoEncodings = codeInfoEncodings;
        this.referenceMapEncoding = referenceMapEncoding;
        this.frameInfoEncodings = frameInfoEncodings;
        this.frameInfoObjectConstants = frameInfoObjectConstants;
        this.frameInfoSourceClasses = frameInfoSourceClasses;
        this.frameInfoSourceMethodNames = frameInfoSourceMethodNames;
        this.frameInfoNames = frameInfoNames;
    }

    static PinnedArray<Byte> pa(byte[] array) {
        return PinnedArrays.fromImageHeapOrPinnedAllocator(array);
    }

    static <T> PinnedObjectArray<T> pa(T[] array) {
        return PinnedArrays.fromImageHeapOrPinnedAllocator(array);
    }

    protected void lookupCodeInfo(long ip, CodeInfoQueryResult codeInfo) {
        CodeInfoDecoder.lookupCodeInfo(pa(codeInfoEncodings), pa(codeInfoIndex), pa(frameInfoEncodings), pa(frameInfoNames), pa(frameInfoObjectConstants),
                        pa(frameInfoSourceClasses), pa(frameInfoSourceMethodNames), pa(referenceMapEncoding), ip, codeInfo);
    }

    public long lookupDeoptimizationEntrypoint(long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(pa(codeInfoEncodings), pa(codeInfoIndex), pa(frameInfoEncodings), pa(frameInfoNames), pa(frameInfoObjectConstants),
                        pa(frameInfoSourceClasses), pa(frameInfoSourceMethodNames), pa(referenceMapEncoding), method, encodedBci, codeInfo);
    }

    public long lookupTotalFrameSize(long ip) {
        return CodeInfoDecoder.lookupTotalFrameSize(pa(codeInfoEncodings), pa(codeInfoIndex), ip);
    }

    protected long lookupExceptionOffset(long ip) {
        return CodeInfoDecoder.lookupExceptionOffset(pa(codeInfoEncodings), pa(codeInfoIndex), ip);
    }

    protected PinnedArray<Byte> getReferenceMapEncoding() {
        return pa(referenceMapEncoding);
    }

    protected long lookupReferenceMapIndex(long ip) {
        return CodeInfoDecoder.lookupReferenceMapIndex(pa(codeInfoEncodings), pa(codeInfoIndex), ip);
    }
}
