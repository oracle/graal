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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.ref.WeakReference;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.PinnedArray;
import com.oracle.svm.core.c.PinnedArrays;
import com.oracle.svm.core.c.PinnedObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.FrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.heap.PinnedAllocator;
import com.oracle.svm.core.os.CommittedMemoryProvider;

import jdk.vm.ci.code.InstalledCode;

public final class RuntimeMethodInfo implements CodeInfo {

    private CodePointer codeStart;
    private UnsignedWord codeSize;

    private PinnedArray<Byte> codeInfoIndex;
    private PinnedArray<Byte> codeInfoEncodings;
    private PinnedArray<Byte> referenceMapEncoding;
    private PinnedArray<Byte> frameInfoEncodings;
    protected Object[] frameInfoObjectConstants;
    protected Class<?>[] frameInfoSourceClasses;
    protected String[] frameInfoSourceMethodNames;
    protected String[] frameInfoNames;

    PinnedArray<Integer> deoptimizationStartOffsets;
    PinnedArray<Byte> deoptimizationEncodings;
    protected Object[] deoptimizationObjectConstants;

    static <T> PinnedObjectArray<T> pa(T[] array) {
        return PinnedArrays.fromImageHeapOrPinnedAllocator(array);
    }

    @Override
    public String getName() {
        return name;
    }

    public int getTier() {
        return tier;
    }

    /**
     * The {@link InstalledCode#getName() name of the InstalledCode}. Stored in a separate field so
     * that it is available even after the code is no longer available.
     *
     * Note that the String is not {@link PinnedAllocator pinned}, so this field must not be
     * accessed during garbage collection.
     */
    protected String name;

    /**
     * The index of the compilation tier that was used to compile the respective code.
     */
    protected int tier;

    /**
     * The handle to the compiled code for the outside world. We only have a weak reference to it,
     * to avoid keeping code alive.
     *
     * Note that the both the InstalledCode and the weak reference are not {@link PinnedAllocator
     * pinned}, so this field must not be accessed during garbage collection.
     */
    protected WeakReference<SubstrateInstalledCode> installedCode;

    /**
     * Provides the GC with the root pointers embedded into the runtime compiled code.
     *
     * This object is accessed during garbage collection, so it must be {@link PinnedAllocator
     * pinned}.
     */
    protected ObjectReferenceWalker constantsWalker;

    /**
     * The pinned allocator used to allocate all code meta data that is accessed during garbage
     * collection. It is {@link PinnedAllocator#release() released} only after the code has been
     * invalidated and it is guaranteed that no more stack frame of the code is present.
     */
    protected PinnedAllocator allocator;

    protected InstalledCodeObserver.InstalledCodeObserverHandle[] codeObserverHandles;

    private RuntimeMethodInfo() {
        throw shouldNotReachHere("Must be allocated with PinnedAllocator");
    }

    public void setData(CodePointer codeStart, UnsignedWord codeSize, SubstrateInstalledCode installedCode, int tier, ObjectReferenceWalker constantsWalker,
                    PinnedAllocator allocator, InstalledCodeObserver.InstalledCodeObserverHandle[] codeObserverHandles) {
        this.codeStart = codeStart;
        this.codeSize = codeSize;
        this.name = installedCode.getName();
        this.installedCode = createInstalledCodeReference(installedCode);
        this.tier = tier;
        this.constantsWalker = constantsWalker;
        this.allocator = allocator;
        assert codeObserverHandles != null;
        this.codeObserverHandles = codeObserverHandles;
    }

    private static WeakReference<SubstrateInstalledCode> createInstalledCodeReference(SubstrateInstalledCode installedCode) {
        return new WeakReference<>(installedCode);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    void freeInstalledCode() {
        CommittedMemoryProvider.get().free(getCodeStart(), getCodeSize(), WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
    }

    @Override
    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    public CodePointer getCodeStart() {
        return codeStart;
    }

    @Override
    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    public UnsignedWord getCodeSize() {
        return codeSize;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    protected CodePointer getCodeEnd() {
        return (CodePointer) ((UnsignedWord) codeStart).add(codeSize);
    }

    @Override
    public boolean contains(CodePointer ip) {
        return CodeInfoAccessor.contains(codeStart, codeSize, ip);
    }

    @Override
    public long relativeIP(CodePointer ip) {
        return CodeInfoAccessor.relativeIP(codeStart, codeSize, ip);
    }

    @Override
    public CodePointer absoluteIP(long relativeIP) {
        return CodeInfoAccessor.absoluteIP(codeStart, relativeIP);
    }

    @Override
    public long initFrameInfoReader(CodePointer ip, ReusableTypeReader frameInfoReader) {
        return CodeInfoAccessor.initFrameInfoReader(codeInfoEncodings, codeInfoIndex, frameInfoEncodings, relativeIP(ip), frameInfoReader);
    }

    @Override
    public FrameInfoQueryResult nextFrameInfo(long entryOffset, ReusableTypeReader frameInfoReader, FrameInfoQueryResultAllocator resultAllocator,
                    ValueInfoAllocator valueInfoAllocator, boolean fetchFirstFrame) {
        return CodeInfoAccessor.nextFrameInfo(codeInfoEncodings, pa(frameInfoNames), pa(frameInfoObjectConstants), pa(frameInfoSourceClasses),
                        pa(frameInfoSourceMethodNames), entryOffset, frameInfoReader, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    @Override
    public void setMetadata(PinnedArray<Byte> codeInfoIndex, PinnedArray<Byte> codeInfoEncodings, PinnedArray<Byte> referenceMapEncoding, PinnedArray<Byte> frameInfoEncodings,
                    Object[] frameInfoObjectConstants, Class<?>[] frameInfoSourceClasses, String[] frameInfoSourceMethodNames, String[] frameInfoNames) {
        this.codeInfoIndex = codeInfoIndex;
        this.codeInfoEncodings = codeInfoEncodings;
        this.referenceMapEncoding = referenceMapEncoding;
        this.frameInfoEncodings = frameInfoEncodings;
        this.frameInfoObjectConstants = frameInfoObjectConstants;
        this.frameInfoSourceClasses = frameInfoSourceClasses;
        this.frameInfoSourceMethodNames = frameInfoSourceMethodNames;
        this.frameInfoNames = frameInfoNames;
    }

    void setDeoptimizationMetadata(PinnedArray<Integer> deoptimizationStartOffsets, PinnedArray<Byte> deoptimizationEncodings, Object[] deoptimizationObjectConstants) {
        this.deoptimizationStartOffsets = deoptimizationStartOffsets;
        this.deoptimizationEncodings = deoptimizationEncodings;
        this.deoptimizationObjectConstants = deoptimizationObjectConstants;
    }

    @Override
    public void lookupCodeInfo(long ip, CodeInfoQueryResult codeInfo) {
        CodeInfoDecoder.lookupCodeInfo(codeInfoEncodings, codeInfoIndex, frameInfoEncodings, pa(frameInfoNames), pa(frameInfoObjectConstants),
                        pa(frameInfoSourceClasses), pa(frameInfoSourceMethodNames), referenceMapEncoding, ip, codeInfo);
    }

    @Override
    public long lookupDeoptimizationEntrypoint(long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(codeInfoEncodings, codeInfoIndex, frameInfoEncodings, pa(frameInfoNames), pa(frameInfoObjectConstants),
                        pa(frameInfoSourceClasses), pa(frameInfoSourceMethodNames), referenceMapEncoding, method, encodedBci, codeInfo);
    }

    @Override
    public long lookupTotalFrameSize(long ip) {
        return CodeInfoDecoder.lookupTotalFrameSize(codeInfoEncodings, codeInfoIndex, ip);
    }

    @Override
    public long lookupExceptionOffset(long ip) {
        return CodeInfoDecoder.lookupExceptionOffset(codeInfoEncodings, codeInfoIndex, ip);
    }

    @Override
    public PinnedArray<Byte> getReferenceMapEncoding() {
        return referenceMapEncoding;
    }

    @Override
    public long lookupReferenceMapIndex(long ip) {
        return CodeInfoDecoder.lookupReferenceMapIndex(codeInfoEncodings, codeInfoIndex, ip);
    }
}
