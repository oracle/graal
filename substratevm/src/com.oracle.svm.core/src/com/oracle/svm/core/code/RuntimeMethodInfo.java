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

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.PinnedArray;
import com.oracle.svm.core.c.PinnedObjectArray;

import jdk.vm.ci.code.InstalledCode;

public final class RuntimeMethodInfo implements CodeInfoHandle {

    private CodePointer codeStart;
    private UnsignedWord codeSize;

    private PinnedArray<Byte> codeInfoIndex;
    private PinnedArray<Byte> codeInfoEncodings;
    private PinnedArray<Byte> referenceMapEncoding;
    private PinnedArray<Byte> frameInfoEncodings;
    private PinnedObjectArray<Object> frameInfoObjectConstants;
    private PinnedObjectArray<Class<?>> frameInfoSourceClasses;
    private PinnedObjectArray<String> frameInfoSourceMethodNames;
    private PinnedObjectArray<String> frameInfoNames;

    private boolean codeConstantsLive = false;
    private PinnedArray<Byte> objectsReferenceMapEncoding;
    private long objectsReferenceMapIndex;

    private PinnedArray<Integer> deoptimizationStartOffsets;
    private PinnedArray<Byte> deoptimizationEncodings;
    private PinnedObjectArray<Object> deoptimizationObjectConstants;

    private int tier;

    private PinnedAllocator allocator;

    /**
     * The "object fields" of this class, managed as an array for simplicity.
     *
     * [0] String: The {@linkplain InstalledCode#getName() name of the InstalledCode}. Stored here
     * so it remains available even after the code is no longer available. Note that the String is
     * not pinned, so this field must not be accessed during garbage collection.
     *
     * [1] WeakReference<SubstrateInstalledCode>: The handle to the compiled code for the outside
     * world. We only have a weak reference to it, to avoid keeping code alive. Note that the both
     * the InstalledCode and the weak reference are not pinned, so this field must not be accessed
     * during garbage collection.
     *
     * [2] InstalledCodeObserverHandle[]: observers for installation and removal of this code.
     */
    private PinnedObjectArray<?> objectFields;

    private RuntimeMethodInfo() {
        throw shouldNotReachHere("Must be allocated with PinnedAllocator");
    }

    void setObjectFields(PinnedObjectArray<?> objectFields) {
        this.objectFields = objectFields;
    }

    PinnedObjectArray<?> getObjectFields() {
        return objectFields;
    }

    int getTier() {
        return tier;
    }

    void setTier(int tier) {
        this.tier = tier;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    CodePointer getCodeStart() {
        return codeStart;
    }

    @Uninterruptible(reason = "called from uninterruptible code", mayBeInlined = true)
    UnsignedWord getCodeSize() {
        return codeSize;
    }

    PinnedArray<Byte> getReferenceMapEncoding() {
        return referenceMapEncoding;
    }

    void setCodeStart(CodePointer codeStart) {
        this.codeStart = codeStart;
    }

    void setCodeSize(UnsignedWord codeSize) {
        this.codeSize = codeSize;
    }

    PinnedArray<Byte> getCodeInfoIndex() {
        return codeInfoIndex;
    }

    void setCodeInfoIndex(PinnedArray<Byte> codeInfoIndex) {
        this.codeInfoIndex = codeInfoIndex;
    }

    PinnedArray<Byte> getCodeInfoEncodings() {
        return codeInfoEncodings;
    }

    void setCodeInfoEncodings(PinnedArray<Byte> codeInfoEncodings) {
        this.codeInfoEncodings = codeInfoEncodings;
    }

    void setReferenceMapEncoding(PinnedArray<Byte> referenceMapEncoding) {
        this.referenceMapEncoding = referenceMapEncoding;
    }

    PinnedArray<Byte> getFrameInfoEncodings() {
        return frameInfoEncodings;
    }

    void setFrameInfoEncodings(PinnedArray<Byte> frameInfoEncodings) {
        this.frameInfoEncodings = frameInfoEncodings;
    }

    PinnedObjectArray<Object> getFrameInfoObjectConstants() {
        return frameInfoObjectConstants;
    }

    void setFrameInfoObjectConstants(PinnedObjectArray<Object> frameInfoObjectConstants) {
        this.frameInfoObjectConstants = frameInfoObjectConstants;
    }

    PinnedObjectArray<Class<?>> getFrameInfoSourceClasses() {
        return frameInfoSourceClasses;
    }

    void setFrameInfoSourceClasses(PinnedObjectArray<Class<?>> frameInfoSourceClasses) {
        this.frameInfoSourceClasses = frameInfoSourceClasses;
    }

    PinnedObjectArray<String> getFrameInfoSourceMethodNames() {
        return frameInfoSourceMethodNames;
    }

    void setFrameInfoSourceMethodNames(PinnedObjectArray<String> frameInfoSourceMethodNames) {
        this.frameInfoSourceMethodNames = frameInfoSourceMethodNames;
    }

    PinnedObjectArray<String> getFrameInfoNames() {
        return frameInfoNames;
    }

    void setFrameInfoNames(PinnedObjectArray<String> frameInfoNames) {
        this.frameInfoNames = frameInfoNames;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isCodeConstantsLive() {
        return codeConstantsLive;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void setCodeConstantsLive(boolean codeConstantsLive) {
        this.codeConstantsLive = codeConstantsLive;
    }

    PinnedArray<Byte> getObjectsReferenceMapEncoding() {
        return objectsReferenceMapEncoding;
    }

    void setObjectsReferenceMapEncoding(PinnedArray<Byte> objectsReferenceMapEncoding) {
        this.objectsReferenceMapEncoding = objectsReferenceMapEncoding;
    }

    long getObjectsReferenceMapIndex() {
        return objectsReferenceMapIndex;
    }

    void setObjectsReferenceMapIndex(long objectsReferenceMapIndex) {
        this.objectsReferenceMapIndex = objectsReferenceMapIndex;
    }

    PinnedArray<Integer> getDeoptimizationStartOffsets() {
        return deoptimizationStartOffsets;
    }

    void setDeoptimizationStartOffsets(PinnedArray<Integer> deoptimizationStartOffsets) {
        this.deoptimizationStartOffsets = deoptimizationStartOffsets;
    }

    PinnedArray<Byte> getDeoptimizationEncodings() {
        return deoptimizationEncodings;
    }

    void setDeoptimizationEncodings(PinnedArray<Byte> deoptimizationEncodings) {
        this.deoptimizationEncodings = deoptimizationEncodings;
    }

    PinnedObjectArray<Object> getDeoptimizationObjectConstants() {
        return deoptimizationObjectConstants;
    }

    void setDeoptimizationObjectConstants(PinnedObjectArray<Object> deoptimizationObjectConstants) {
        this.deoptimizationObjectConstants = deoptimizationObjectConstants;
    }

    PinnedAllocator getAllocator() {
        return allocator;
    }

    void setAllocator(PinnedAllocator allocator) {
        this.allocator = allocator;
    }
}
