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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.c.PinnedArray;
import com.oracle.svm.core.c.PinnedArrays;
import com.oracle.svm.core.c.PinnedObjectArray;
import com.oracle.svm.core.log.Log;

public class ImageCodeInfo implements CodeInfoAccessor {

    /**
     * There is only one instance for all image code, so we don't need any handles. However, for
     * accesses via {@link CodeInfoAccessor}, we provide this handle value.
     */
    public static final CodeInfoHandle SINGLETON_HANDLE = null;

    public static final String CODE_INFO_NAME = "image code";

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

    @Platforms(Platform.HOSTED_ONLY.class)
    ImageCodeInfo() {
    }

    public CodeInfoAccessor getAccessor() {
        return this;
    }

    private static PinnedArray<Byte> pa(byte[] array) {
        return PinnedArrays.fromImageHeap(array);
    }

    private static <T> PinnedObjectArray<T> pa(T[] array) {
        return PinnedArrays.fromImageHeap(array);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setCodeLocation(CodePointer codeStart, UnsignedWord codeSize) {
        this.codeStart = codeStart;
        this.codeSize = codeSize;
    }

    /** Walk the image code with a MemoryWalker. */
    public boolean walkImageCode(MemoryWalker.Visitor visitor) {
        return visitor.visitImageCode(this, ImageSingletons.lookup(MemoryWalkerAccessImpl.class));
    }

    @Override
    public String getName(CodeInfoHandle handle) {
        return CODE_INFO_NAME;
    }

    @Override
    public CodePointer getCodeStart(CodeInfoHandle handle) {
        return codeStart;
    }

    @Override
    public UnsignedWord getCodeSize(CodeInfoHandle handle) {
        return codeSize;
    }

    @Override
    public boolean contains(CodeInfoHandle handle, CodePointer ip) {
        return CodeInfoAccessor.contains(codeStart, codeSize, ip);
    }

    @Override
    public long relativeIP(CodeInfoHandle handle, CodePointer ip) {
        return CodeInfoAccessor.relativeIP(codeStart, codeSize, ip);
    }

    @Override
    public long initFrameInfoReader(CodeInfoHandle handle, CodePointer ip, ReusableTypeReader frameInfoReader) {
        return CodeInfoAccessor.initFrameInfoReader(pa(codeInfoEncodings), pa(codeInfoIndex), pa(frameInfoEncodings), CodeInfoAccessor.relativeIP(codeStart, codeSize, ip), frameInfoReader);
    }

    @Override
    public FrameInfoQueryResult nextFrameInfo(CodeInfoHandle handle, long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoDecoder.FrameInfoQueryResultAllocator resultAllocator, FrameInfoDecoder.ValueInfoAllocator valueInfoAllocator, boolean fetchFirstFrame) {

        return CodeInfoAccessor.nextFrameInfo(pa(codeInfoEncodings), pa(frameInfoNames), pa(frameInfoObjectConstants), pa(frameInfoSourceClasses),
                        pa(frameInfoSourceMethodNames), entryOffset, frameInfoReader, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    @Override
    public void setMetadata(CodeInfoHandle installTarget, PinnedArray<Byte> codeInfoIndex, PinnedArray<Byte> codeInfoEncodings, PinnedArray<Byte> referenceMapEncoding,
                    PinnedArray<Byte> frameInfoEncodings, PinnedObjectArray<Object> frameInfoObjectConstants, PinnedObjectArray<Class<?>> frameInfoSourceClasses,
                    PinnedObjectArray<String> frameInfoSourceMethodNames, PinnedObjectArray<String> frameInfoNames) {

        this.codeInfoIndex = PinnedArrays.getHostedArray(codeInfoIndex);
        this.codeInfoEncodings = PinnedArrays.getHostedArray(codeInfoEncodings);
        this.referenceMapEncoding = PinnedArrays.getHostedArray(referenceMapEncoding);
        this.frameInfoEncodings = PinnedArrays.getHostedArray(frameInfoEncodings);
        this.frameInfoObjectConstants = PinnedArrays.getHostedArray(frameInfoObjectConstants);
        this.frameInfoSourceClasses = PinnedArrays.getHostedArray(frameInfoSourceClasses);
        this.frameInfoSourceMethodNames = PinnedArrays.getHostedArray(frameInfoSourceMethodNames);
        this.frameInfoNames = PinnedArrays.getHostedArray(frameInfoNames);
    }

    @Override
    public void lookupCodeInfo(CodeInfoHandle handle, long ip, CodeInfoQueryResult codeInfo) {
        CodeInfoDecoder.lookupCodeInfo(pa(codeInfoEncodings), pa(codeInfoIndex), pa(frameInfoEncodings), pa(frameInfoNames), pa(frameInfoObjectConstants),
                        pa(frameInfoSourceClasses), pa(frameInfoSourceMethodNames), pa(referenceMapEncoding), ip, codeInfo);
    }

    @Override
    public CodeInfoHandle lookupCodeInfo(CodePointer ip) {
        return SINGLETON_HANDLE;
    }

    @Override
    public boolean isNone(CodeInfoHandle handle) {
        return false;
    }

    @Override
    public long lookupExceptionOffset(CodeInfoHandle handle, long ip) {
        return CodeInfoDecoder.lookupExceptionOffset(pa(codeInfoEncodings), pa(codeInfoIndex), ip);
    }

    @Override
    public PinnedArray<Byte> getReferenceMapEncoding(CodeInfoHandle handle) {
        return pa(referenceMapEncoding);
    }

    @Override
    public long lookupReferenceMapIndex(CodeInfoHandle handle, long ip) {
        return CodeInfoDecoder.lookupReferenceMapIndex(pa(codeInfoEncodings), pa(codeInfoIndex), ip);
    }

    @Override
    public CodePointer absoluteIP(CodeInfoHandle handle, long relativeIP) {
        return CodeInfoAccessor.absoluteIP(codeStart, relativeIP);
    }

    @Override
    public long lookupDeoptimizationEntrypoint(CodeInfoHandle handle, long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(pa(codeInfoEncodings), pa(codeInfoIndex), pa(frameInfoEncodings), pa(frameInfoNames), pa(frameInfoObjectConstants),
                        pa(frameInfoSourceClasses), pa(frameInfoSourceMethodNames), pa(referenceMapEncoding), method, encodedBci, codeInfo);
    }

    @Override
    public long lookupTotalFrameSize(CodeInfoHandle handle, long ip) {
        return CodeInfoDecoder.lookupTotalFrameSize(pa(codeInfoEncodings), pa(codeInfoIndex), ip);
    }

    @Override
    public Log log(CodeInfoHandle handle, Log log) {
        return log.object(this);
    }

    /** Methods for MemoryWalker to access image code information. */
    public static final class MemoryWalkerAccessImpl implements MemoryWalker.ImageCodeAccess<ImageCodeInfo> {

        /** A private constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected MemoryWalkerAccessImpl() {
            super();
        }

        @Override
        public UnsignedWord getStart(ImageCodeInfo imageCodeInfo) {
            return (UnsignedWord) imageCodeInfo.codeStart;
        }

        @Override
        public UnsignedWord getSize(ImageCodeInfo imageCodeInfo) {
            return imageCodeInfo.codeSize;
        }

        @Override
        public String getRegion(ImageCodeInfo imageCodeInfo) {
            return CODE_INFO_NAME;
        }
    }
}

@AutomaticFeature
class ImageCodeInfoMemoryWalkerAccessFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ImageCodeInfo.MemoryWalkerAccessImpl.class, new ImageCodeInfo.MemoryWalkerAccessImpl());
    }
}
