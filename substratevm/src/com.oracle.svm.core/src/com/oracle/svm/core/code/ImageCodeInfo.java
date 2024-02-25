/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CIsolateData;
import com.oracle.svm.core.c.CIsolateDataFactory;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

public class ImageCodeInfo {
    public static final String CODE_INFO_NAME = "image code";

    private final CIsolateData<CodeInfoImpl> runtimeCodeInfo = CIsolateDataFactory.createStruct("runtimeCodeInfo", CodeInfoImpl.class);

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final HostedImageCodeInfo hostedImageCodeInfo = new HostedImageCodeInfo();

    @UnknownPrimitiveField(availability = AfterCompilation.class) private CodePointer codeStart;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord entryPointOffset;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord codeSize;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord dataOffset;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord dataSize;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord codeAndDataMemorySize;

    private final Object[] objectFields;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] codeInfoIndex;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] codeInfoEncodings;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] referenceMapEncoding;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] frameInfoEncodings;
    @UnknownObjectField(availability = AfterCompilation.class) Object[] frameInfoObjectConstants;
    @UnknownObjectField(availability = AfterCompilation.class) Class<?>[] frameInfoSourceClasses;
    @UnknownObjectField(availability = AfterCompilation.class) String[] frameInfoSourceMethodNames;

    @Platforms(Platform.HOSTED_ONLY.class)
    ImageCodeInfo() {
        NonmovableObjectArray<Object> objfields = NonmovableArrays.createObjectArray(Object[].class, CodeInfoImpl.OBJFIELDS_COUNT, NmtCategory.Code);
        NonmovableArrays.setObject(objfields, CodeInfoImpl.NAME_OBJFIELD, CODE_INFO_NAME);
        // The image code info is never invalidated, so we consider it as always tethered.
        NonmovableArrays.setObject(objfields, CodeInfoImpl.TETHER_OBJFIELD, new CodeInfoTether(true));
        // no InstalledCode for image code
        objectFields = NonmovableArrays.getHostedArray(objfields);
    }

    @Uninterruptible(reason = "Executes during isolate creation.")
    CodeInfo prepareCodeInfo() {
        CodeInfoImpl info = runtimeCodeInfo.get();
        assert info.getCodeStart().isNull() : "already initialized";

        info.setObjectFields(NonmovableArrays.fromImageHeap(objectFields));
        info.setCodeStart(codeStart);
        info.setCodeSize(codeSize);
        info.setDataOffset(dataOffset);
        info.setDataSize(dataSize);
        info.setCodeAndDataMemorySize(codeAndDataMemorySize);
        info.setCodeInfoIndex(NonmovableArrays.fromImageHeap(codeInfoIndex));
        info.setCodeInfoEncodings(NonmovableArrays.fromImageHeap(codeInfoEncodings));
        info.setStackReferenceMapEncoding(NonmovableArrays.fromImageHeap(referenceMapEncoding));
        info.setFrameInfoEncodings(NonmovableArrays.fromImageHeap(frameInfoEncodings));
        info.setFrameInfoObjectConstants(NonmovableArrays.fromImageHeap(frameInfoObjectConstants));
        info.setFrameInfoSourceClasses(NonmovableArrays.fromImageHeap(frameInfoSourceClasses));
        info.setFrameInfoSourceMethodNames(NonmovableArrays.fromImageHeap(frameInfoSourceMethodNames));

        return info;
    }

    /**
     * Use {@link CodeInfoTable#getImageCodeInfo()} and {@link CodeInfoAccess#getCodeStart} instead.
     * This method is intended only for the early stages of VM initialization when
     * {@link #prepareCodeInfo()} has not yet run.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodePointer getCodeStart() {
        return codeStart;
    }

    /**
     * Use {@link CodeInfoTable#getImageCodeInfo()} and
     * {@link CodeInfoAccess#getStackReferenceMapEncoding} instead. This method is intended only for
     * the early stages of VM initialization when {@link #prepareCodeInfo()} has not yet run.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public NonmovableArray<Byte> getStackReferenceMapEncoding() {
        return NonmovableArrays.fromImageHeap(referenceMapEncoding);
    }

    public HostedImageCodeInfo getHostedImageCodeInfo() {
        return hostedImageCodeInfo;
    }

    public List<Integer> getTotalByteArrayLengths() {
        return List.of(codeInfoIndex.length, codeInfoEncodings.length, referenceMapEncoding.length, frameInfoEncodings.length);
    }

    /**
     * Pure-hosted {@link CodeInfo} to collect and persist image code metadata in
     * {@link ImageCodeInfo} and provide accesses during image generation.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public class HostedImageCodeInfo implements CodeInfoImpl {
        @Override
        public CodePointer getCodeStart() {
            return codeStart;
        }

        @Override
        public UnsignedWord getCodeSize() {
            return codeSize;
        }

        @Override
        public UnsignedWord getDataOffset() {
            return dataOffset;
        }

        @Override
        public UnsignedWord getDataSize() {
            return dataSize;
        }

        @Override
        public UnsignedWord getCodeAndDataMemorySize() {
            return codeAndDataMemorySize;
        }

        @Override
        public NonmovableArray<Byte> getStackReferenceMapEncoding() {
            return NonmovableArrays.fromImageHeap(referenceMapEncoding);
        }

        @Override
        public void setCodeStart(CodePointer value) {
            codeStart = value;
        }

        @Override
        public UnsignedWord getCodeEntryPointOffset() {
            return entryPointOffset;
        }

        @Override
        public void setCodeSize(UnsignedWord value) {
            codeSize = value;
        }

        @Override
        public void setCodeEntryPointOffset(UnsignedWord offset) {
            entryPointOffset = offset;
        }

        @Override
        public void setDataOffset(UnsignedWord value) {
            dataOffset = value;
        }

        @Override
        public void setDataSize(UnsignedWord value) {
            dataSize = value;
        }

        @Override
        public void setCodeAndDataMemorySize(UnsignedWord value) {
            codeAndDataMemorySize = value;
        }

        @Override
        public NonmovableArray<Byte> getCodeInfoIndex() {
            return NonmovableArrays.fromImageHeap(codeInfoIndex);
        }

        @Override
        public void setCodeInfoIndex(NonmovableArray<Byte> array) {
            codeInfoIndex = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableArray<Byte> getCodeInfoEncodings() {
            return NonmovableArrays.fromImageHeap(codeInfoEncodings);
        }

        @Override
        public void setCodeInfoEncodings(NonmovableArray<Byte> array) {
            codeInfoEncodings = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public void setStackReferenceMapEncoding(NonmovableArray<Byte> array) {
            referenceMapEncoding = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableArray<Byte> getFrameInfoEncodings() {
            return NonmovableArrays.fromImageHeap(frameInfoEncodings);
        }

        @Override
        public void setFrameInfoEncodings(NonmovableArray<Byte> array) {
            frameInfoEncodings = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableObjectArray<Object> getFrameInfoObjectConstants() {
            return NonmovableArrays.fromImageHeap(frameInfoObjectConstants);
        }

        @Override
        public void setFrameInfoObjectConstants(NonmovableObjectArray<Object> array) {
            frameInfoObjectConstants = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableObjectArray<Class<?>> getFrameInfoSourceClasses() {
            return NonmovableArrays.fromImageHeap(frameInfoSourceClasses);
        }

        @Override
        public void setFrameInfoSourceClasses(NonmovableObjectArray<Class<?>> array) {
            frameInfoSourceClasses = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableObjectArray<String> getFrameInfoSourceMethodNames() {
            return NonmovableArrays.fromImageHeap(frameInfoSourceMethodNames);
        }

        @Override
        public void setFrameInfoSourceMethodNames(NonmovableObjectArray<String> array) {
            frameInfoSourceMethodNames = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public void setObjectFields(NonmovableObjectArray<Object> fields) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public NonmovableObjectArray<Object> getObjectFields() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public int getTier() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setTier(int tier) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public int getState() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setState(int state) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public NonmovableArray<Byte> getCodeConstantsReferenceMapEncoding() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setCodeConstantsReferenceMapEncoding(NonmovableArray<Byte> objectsReferenceMapEncoding) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public long getCodeConstantsReferenceMapIndex() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setCodeConstantsReferenceMapIndex(long objectsReferenceMapIndex) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public NonmovableArray<Integer> getDeoptimizationStartOffsets() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setDeoptimizationStartOffsets(NonmovableArray<Integer> deoptimizationStartOffsets) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public NonmovableArray<Byte> getDeoptimizationEncodings() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setDeoptimizationEncodings(NonmovableArray<Byte> deoptimizationEncodings) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public NonmovableObjectArray<Object> getDeoptimizationObjectConstants() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setDeoptimizationObjectConstants(NonmovableObjectArray<Object> deoptimizationObjectConstants) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public NonmovableArray<InstalledCodeObserver.InstalledCodeObserverHandle> getCodeObserverHandles() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setCodeObserverHandles(NonmovableArray<InstalledCodeObserver.InstalledCodeObserverHandle> handles) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public Word getGCData() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public void setAllObjectsAreInImageHeap(boolean value) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public boolean getAllObjectsAreInImageHeap() {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean isNonNull() {
            return !isNull();
        }

        @Override
        public boolean equal(ComparableWord val) {
            throw VMError.shouldNotReachHere("not supported during image generation");
        }

        @Override
        public boolean notEqual(ComparableWord val) {
            throw VMError.shouldNotReachHere("not supported during image generation");
        }

        @Override
        public long rawValue() {
            throw VMError.shouldNotReachHere("not supported during image generation");
        }
    }
}
