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

import java.util.EnumSet;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

public class ImageCodeInfo implements MultiLayeredImageSingleton, UnsavedSingleton {
    public static final String CODE_INFO_NAME = "image code";

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final HostedImageCodeInfo hostedImageCodeInfo = new HostedImageCodeInfo();

    @UnknownPrimitiveField(availability = AfterCompilation.class) private CodePointer codeStart;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord entryPointOffset;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord codeSize;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord dataOffset;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord dataSize;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private UnsignedWord codeAndDataMemorySize;
    @UnknownPrimitiveField(availability = AfterCompilation.class) private int methodTableFirstId;

    private final Object[] objectFields;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] codeInfoIndex;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] codeInfoEncodings;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] referenceMapEncoding;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] frameInfoEncodings;
    @UnknownObjectField(availability = AfterCompilation.class) Object[] objectConstants;
    @UnknownObjectField(availability = AfterCompilation.class) Class<?>[] classes;
    @UnknownObjectField(availability = AfterCompilation.class) String[] memberNames;
    @UnknownObjectField(availability = AfterCompilation.class) String[] otherStrings;
    @UnknownObjectField(availability = AfterCompilation.class) byte[] methodTable;

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
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            ImageCodeInfo imageCodeInfo = CodeInfoTable.getImageCodeCache();
            CodeInfoImpl codeInfo = ImageCodeInfoStorage.get();
            return ImageCodeInfo.prepareCodeInfo0(imageCodeInfo, codeInfo, WordFactory.nullPointer());
        } else {
            ImageCodeInfo[] imageCodeInfos = MultiLayeredImageSingleton.getAllLayers(ImageCodeInfo.class);
            ImageCodeInfoStorage[] runtimeCodeInfos = MultiLayeredImageSingleton.getAllLayers(ImageCodeInfoStorage.class);
            int size = imageCodeInfos.length;
            for (int i = 0; i < size; i++) {
                ImageCodeInfo imageCodeInfo = imageCodeInfos[i];
                CodeInfoImpl codeInfoImpl = runtimeCodeInfos[i].getData();
                CodeInfoImpl nextCodeInfoImpl = i + 1 < size ? runtimeCodeInfos[i + 1].getData() : WordFactory.nullPointer();

                ImageCodeInfo.prepareCodeInfo0(imageCodeInfo, codeInfoImpl, nextCodeInfoImpl);
            }
            return runtimeCodeInfos[0].getData();
        }
    }

    @Uninterruptible(reason = "Executes during isolate creation.")
    private static CodeInfo prepareCodeInfo0(ImageCodeInfo imageCodeInfo, CodeInfoImpl infoImpl, CodeInfo next) {
        assert infoImpl.getCodeStart().isNull() : "already initialized";

        infoImpl.setObjectFields(NonmovableArrays.fromImageHeap(imageCodeInfo.objectFields));
        infoImpl.setCodeStart(imageCodeInfo.codeStart);
        infoImpl.setCodeSize(imageCodeInfo.codeSize);
        infoImpl.setDataOffset(imageCodeInfo.dataOffset);
        infoImpl.setDataSize(imageCodeInfo.dataSize);
        infoImpl.setCodeAndDataMemorySize(imageCodeInfo.codeAndDataMemorySize);
        infoImpl.setCodeInfoIndex(NonmovableArrays.fromImageHeap(imageCodeInfo.codeInfoIndex));
        infoImpl.setCodeInfoEncodings(NonmovableArrays.fromImageHeap(imageCodeInfo.codeInfoEncodings));
        infoImpl.setStackReferenceMapEncoding(NonmovableArrays.fromImageHeap(imageCodeInfo.referenceMapEncoding));
        infoImpl.setFrameInfoEncodings(NonmovableArrays.fromImageHeap(imageCodeInfo.frameInfoEncodings));
        infoImpl.setObjectConstants(NonmovableArrays.fromImageHeap(imageCodeInfo.objectConstants));
        infoImpl.setClasses(NonmovableArrays.fromImageHeap(imageCodeInfo.classes));
        infoImpl.setMemberNames(NonmovableArrays.fromImageHeap(imageCodeInfo.memberNames));
        infoImpl.setOtherStrings(NonmovableArrays.fromImageHeap(imageCodeInfo.otherStrings));
        infoImpl.setMethodTable(NonmovableArrays.fromImageHeap(imageCodeInfo.methodTable));
        infoImpl.setMethodTableFirstId(imageCodeInfo.methodTableFirstId);
        infoImpl.setIsAOTImageCode(true);
        infoImpl.setNextImageCodeInfo(next);

        return infoImpl;
    }

    /**
     * Use {@link CodeInfoTable#getImageCodeInfo} and {@link CodeInfoAccess#getCodeStart} instead.
     * This method is intended only for VM-internal usage.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodePointer getCodeStart() {
        return codeStart;
    }

    public HostedImageCodeInfo getHostedImageCodeInfo() {
        return hostedImageCodeInfo;
    }

    public List<Integer> getTotalByteArrayLengths() {
        return List.of(codeInfoIndex.length, codeInfoEncodings.length, referenceMapEncoding.length, frameInfoEncodings.length, methodTable.length);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
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
        public NonmovableObjectArray<Object> getObjectConstants() {
            return NonmovableArrays.fromImageHeap(objectConstants);
        }

        @Override
        public void setObjectConstants(NonmovableObjectArray<Object> array) {
            objectConstants = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableObjectArray<Class<?>> getClasses() {
            return NonmovableArrays.fromImageHeap(classes);
        }

        @Override
        public void setClasses(NonmovableObjectArray<Class<?>> array) {
            classes = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableObjectArray<String> getMemberNames() {
            return NonmovableArrays.fromImageHeap(memberNames);
        }

        @Override
        public void setMemberNames(NonmovableObjectArray<String> array) {
            memberNames = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableObjectArray<String> getOtherStrings() {
            return NonmovableArrays.fromImageHeap(otherStrings);
        }

        @Override
        public void setOtherStrings(NonmovableObjectArray<String> array) {
            otherStrings = NonmovableArrays.getHostedArray(array);
        }

        @Override
        public NonmovableArray<Byte> getMethodTable() {
            return NonmovableArrays.fromImageHeap(methodTable);
        }

        @Override
        public void setMethodTable(NonmovableArray<Byte> methods) {
            methodTable = NonmovableArrays.getHostedArray(methods);
        }

        @Override
        public int getMethodTableFirstId() {
            return methodTableFirstId;
        }

        @Override
        public void setMethodTableFirstId(int methodId) {
            methodTableFirstId = methodId;
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
        public void setIsAOTImageCode(boolean value) {
            throw VMError.shouldNotReachHere("not supported for image code");
        }

        @Override
        public boolean getIsAOTImageCode() {
            return true;
        }

        @Override
        public void setNextImageCodeInfo(CodeInfo next) {
            throw VMError.shouldNotReachHere("not supported during image generation");
        }

        @Override
        public CodeInfo getNextImageCodeInfo() {
            throw VMError.shouldNotReachHere("not supported during image generation");
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
