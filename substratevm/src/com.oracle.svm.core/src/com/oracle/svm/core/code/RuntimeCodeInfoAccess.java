/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * This class contains methods that only make sense for runtime compiled code.
 */
public final class RuntimeCodeInfoAccess {
    private RuntimeCodeInfoAccess() {
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static SubstrateInstalledCode getInstalledCode(CodeInfo info) {
        return CodeInfoAccess.getObjectField(info, CodeInfoImpl.INSTALLEDCODE_OBJFIELD);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<InstalledCodeObserverHandle> getCodeObserverHandles(CodeInfo info) {
        return cast(info).getCodeObserverHandles();
    }

    public static void initialize(CodeInfo info, Pointer codeStart, int entryPointOffset, int codeSize, int dataOffset, int dataSize, int codeAndDataMemorySize,
                    int tier, NonmovableArray<InstalledCodeObserverHandle> observerHandles, boolean allObjectsAreInImageHeap) {

        CodeInfoImpl impl = cast(info);
        impl.setCodeStart((CodePointer) codeStart);
        impl.setCodeEntryPointOffset(Word.unsigned(entryPointOffset));
        impl.setCodeSize(Word.unsigned(codeSize));
        impl.setDataOffset(Word.unsigned(dataOffset));
        impl.setDataSize(Word.unsigned(dataSize));
        impl.setCodeAndDataMemorySize(Word.unsigned(codeAndDataMemorySize));
        impl.setTier(tier);
        impl.setCodeObserverHandles(observerHandles);
        impl.setAllObjectsAreInImageHeap(allObjectsAreInImageHeap);
    }

    public static void setCodeObjectConstantsInfo(CodeInfo info, NonmovableArray<Byte> refMapEncoding, long refMapIndex) {
        CodeInfoImpl impl = cast(info);
        assert impl.getCodeStart().isNonNull() : "null";
        impl.setCodeConstantsReferenceMapEncoding(refMapEncoding);
        impl.setCodeConstantsReferenceMapIndex(refMapIndex);
    }

    public static NonmovableArray<Byte> getCodeConstantsReferenceMapEncoding(CodeInfo info) {
        return cast(info).getCodeConstantsReferenceMapEncoding();
    }

    public static long getCodeConstantsReferenceMapIndex(CodeInfo info) {
        return cast(info).getCodeConstantsReferenceMapIndex();
    }

    public static NonmovableArray<Byte> getDeoptimizationEncodings(CodeInfo info) {
        return cast(info).getDeoptimizationEncodings();
    }

    public static NonmovableArray<Integer> getDeoptimizationStartOffsets(CodeInfo info) {
        return cast(info).getDeoptimizationStartOffsets();
    }

    public static NonmovableObjectArray<Object> getDeoptimizationObjectConstants(CodeInfo info) {
        return cast(info).getDeoptimizationObjectConstants();
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    public static void setDeoptimizationMetadata(CodeInfo info, NonmovableArray<Integer> startOffsets, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants) {
        CodeInfoImpl impl = cast(info);
        impl.setDeoptimizationStartOffsets(startOffsets);
        impl.setDeoptimizationEncodings(encodings);
        impl.setDeoptimizationObjectConstants(objectConstants);
        if (!SubstrateUtil.HOSTED) {
            // notify the GC about the deopt metadata that is now live
            Heap.getHeap().getRuntimeCodeInfoGCSupport().registerDeoptMetadata(impl);
        }
    }

    public static CodeInfoTether beforeInstallInCurrentIsolate(CodeInfo info, SubstrateInstalledCode installedCode) {
        CodeInfoTether tether = new CodeInfoTether(true);
        setObjectData(info, tether, installedCode.getName(), installedCode);
        return tether;
    }

    @Uninterruptible(reason = "Makes the object data visible to the GC.")
    private static void setObjectData(CodeInfo info, CodeInfoTether tether, String name, SubstrateInstalledCode installedCode) {
        NonmovableObjectArray<Object> objectFields = cast(info).getObjectFields();
        NonmovableArrays.setObject(objectFields, CodeInfoImpl.TETHER_OBJFIELD, tether);
        NonmovableArrays.setObject(objectFields, CodeInfoImpl.NAME_OBJFIELD, name);
        NonmovableArrays.setObject(objectFields, CodeInfoImpl.INSTALLEDCODE_OBJFIELD, installedCode);
        if (!SubstrateUtil.HOSTED) {
            // after setting all the object data, notify the GC
            Heap.getHeap().getRuntimeCodeInfoGCSupport().registerObjectFields(info);
        }
    }

    public static Object[] prepareHeapObjectData(CodeInfoTether tether, String name, SubstrateInstalledCode installedCode) {
        Object[] objectFields = new Object[CodeInfoImpl.OBJFIELDS_COUNT];
        objectFields[CodeInfoImpl.TETHER_OBJFIELD] = tether;
        objectFields[CodeInfoImpl.NAME_OBJFIELD] = name;
        objectFields[CodeInfoImpl.INSTALLEDCODE_OBJFIELD] = installedCode;
        return objectFields;
    }

    public static boolean areAllObjectsOnImageHeap(CodeInfo info) {
        return cast(info).getAllObjectsAreInImageHeap();
    }

    /**
     * Walks all strong references in a {@link CodeInfo} object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void walkStrongReferences(CodeInfo info, ObjectReferenceVisitor visitor) {
        NonmovableArrays.walkUnmanagedObjectArray(cast(info).getObjectFields(), visitor, CodeInfoImpl.FIRST_STRONGLY_REFERENCED_OBJFIELD, CodeInfoImpl.STRONGLY_REFERENCED_OBJFIELD_COUNT);
    }

    /**
     * Walks all weak references in a {@link CodeInfo} object.
     */
    @DuplicatedInNativeCode
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void walkWeakReferences(CodeInfo info, ObjectReferenceVisitor visitor) {
        CodeInfoImpl impl = cast(info);
        NonmovableArrays.walkUnmanagedObjectArray(impl.getObjectFields(), visitor, CodeInfoImpl.FIRST_WEAKLY_REFERENCED_OBJFIELD, CodeInfoImpl.WEAKLY_REFERENCED_OBJFIELD_COUNT);
        if (CodeInfoAccess.isAliveState(impl.getState())) {
            CodeReferenceMapDecoder.walkOffsetsFromPointer(impl.getCodeStart(), impl.getCodeConstantsReferenceMapEncoding(), impl.getCodeConstantsReferenceMapIndex(), visitor, null);
        }
        NonmovableArrays.walkUnmanagedObjectArray(impl.getObjectConstants(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(impl.getClasses(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(impl.getMemberNames(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(impl.getOtherStrings(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(impl.getDeoptimizationObjectConstants(), visitor);
    }

    /**
     * This method only walks the tether. You typically want to use {@link #walkStrongReferences}
     * and/or {@link #walkWeakReferences} instead.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void walkTether(CodeInfo info, ObjectReferenceVisitor visitor) {
        NonmovableArrays.walkUnmanagedObjectArray(cast(info).getObjectFields(), visitor, CodeInfoImpl.TETHER_OBJFIELD, 1);
    }

    /**
     * This method only visits a very specific subset of all the references, so you typically want
     * to use {@link #walkStrongReferences} and/or {@link #walkWeakReferences} instead.
     */
    public static void walkObjectFields(CodeInfo info, ObjectReferenceVisitor visitor) {
        NonmovableArrays.walkUnmanagedObjectArray(cast(info).getObjectFields(), visitor);
    }

    public static CodeInfo allocateMethodInfo() {
        NonmovableObjectArray<Object> objectFields = NonmovableArrays.createObjectArray(Object[].class, CodeInfoImpl.OBJFIELDS_COUNT, NmtCategory.Code);
        return allocateMethodInfo(objectFields);
    }

    public static CodeInfo allocateMethodInfo(NonmovableObjectArray<Object> objectData) {
        CodeInfoImpl info = NativeMemory.calloc(CodeInfoAccess.getSizeOfCodeInfo(), NmtCategory.Code);

        assert objectData.isNonNull() && NonmovableArrays.lengthOf(objectData) == CodeInfoImpl.OBJFIELDS_COUNT;
        info.setObjectFields(objectData);

        // Make the object visible to the GC (before writing any heap data into the object).
        RuntimeCodeInfoMemory.singleton().add(info);
        return info;
    }

    @Uninterruptible(reason = "Prevent the GC from running - otherwise, it could accidentally visit the freed memory.")
    static void markAsRemovedFromCodeCache(CodeInfo info) {
        CodeInfoImpl impl = cast(info);
        assert CodeInfoAccess.isAliveState(impl.getState()) || impl.getState() == CodeInfo.STATE_PENDING_REMOVAL_FROM_CODE_CACHE : "unexpected state (probably already released)";
        /* We can't free any data because only the GC is allowed to free CodeInfo data. */
        CodeInfoAccess.setState(info, CodeInfo.STATE_REMOVED_FROM_CODE_CACHE);
    }

    public static CodePointer allocateCodeMemory(UnsignedWord size) {
        return (CodePointer) CommittedMemoryProvider.get().allocateExecutableMemory(size, Word.unsigned(SubstrateOptions.runtimeCodeAlignment()));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void releaseCodeMemory(CodePointer codeStart, UnsignedWord codeSize) {
        CommittedMemoryProvider.get().freeExecutableMemory(codeStart, codeSize, Word.unsigned(SubstrateOptions.runtimeCodeAlignment()));
    }

    public static void makeCodeMemoryExecutableReadOnly(CodePointer codeStart, UnsignedWord codeSize) {
        protectCodeMemory(codeStart, codeSize, VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.EXECUTE);
    }

    public static void makeCodeMemoryExecutableWritable(CodePointer start, UnsignedWord size) {
        VMError.guarantee(RuntimeCodeCache.Options.WriteableCodeCache.getValue(), "memory must not be writable and executable at the same time unless we have a writable code cache");
        protectCodeMemory(start, size, VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE | VirtualMemoryProvider.Access.EXECUTE);
    }

    private static void protectCodeMemory(CodePointer codeStart, UnsignedWord codeSize, int permissions) {
        int result = VirtualMemoryProvider.get().protect(codeStart, codeSize, permissions);
        if (result != 0) {
            throw VMError.shouldNotReachHere("Failed to modify protection of code memory. This may be caused by " +
                            "a. a too restrictive OS-limit of allowed memory mappings (see vm.max_map_count on Linux), " +
                            "b. a too strict security policy if you are running on Security-Enhanced Linux (SELinux), or " +
                            "c. a Native Image internal error.");
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static void releaseMethodInfoOnTearDown(CodeInfo info) {
        InstalledCodeObserverSupport.removeObserversOnTearDown(getCodeObserverHandles(info));

        assert ((CodeInfoTether) UntetheredCodeInfoAccess.getTetherUnsafe(info)).getCount() == 1 : "CodeInfo tether must not be referenced by non-teardown code.";
        free(info);
    }

    public interface NonmovableArrayAction {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void apply(NonmovableArray<?> array);
    }

    private static final NonmovableArrayAction RELEASE_ACTION = new NonmovableArrayAction() {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void apply(NonmovableArray<?> array) {
            NonmovableArrays.releaseUnmanagedArray(array);
        }
    };

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void free(CodeInfo info) {
        CodeInfoImpl impl = cast(info);

        /* Free the code observers handles unconditionally (they are never in the image heap). */
        NonmovableArrays.releaseUnmanagedArray(impl.getCodeObserverHandles());
        impl.setCodeObserverHandles(NonmovableArrays.nullArray());

        releaseCodeMemory(impl.getCodeStart(), impl.getCodeAndDataMemorySize());

        if (!impl.getAllObjectsAreInImageHeap()) {
            forEachArray(info, RELEASE_ACTION);
        }

        impl.setState(CodeInfo.STATE_FREED);
        NullableNativeMemory.free(info);
    }

    private static final NonmovableArrayAction GUARANTEE_ALL_OBJECTS_IN_IMAGE_HEAP_ACTION = new NonmovableArrayAction() {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void apply(NonmovableArray<?> arg) {
            NonmovableObjectArray<?> array = (NonmovableObjectArray<?>) arg;
            if (array.isNonNull()) {
                int length = NonmovableArrays.lengthOf(array);
                for (int i = 0; i < length; i++) {
                    Object obj = NonmovableArrays.getObject(array, i);
                    VMError.guarantee(obj == null || Heap.getHeap().isInImageHeap(obj));
                }
            }
        }
    };

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void guaranteeAllObjectsInImageHeap(CodeInfo info) {
        forEachObjectArray(info, GUARANTEE_ALL_OBJECTS_IN_IMAGE_HEAP_ACTION);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void forEachArray(CodeInfo info, NonmovableArrayAction action) {
        CodeInfoImpl impl = cast(info);
        action.apply(impl.getCodeInfoIndex());
        action.apply(impl.getCodeInfoEncodings());
        action.apply(impl.getStackReferenceMapEncoding());
        action.apply(impl.getFrameInfoEncodings());
        action.apply(impl.getDeoptimizationStartOffsets());
        action.apply(impl.getDeoptimizationEncodings());
        action.apply(impl.getCodeConstantsReferenceMapEncoding());
        action.apply(impl.getCodeObserverHandles());
        action.apply(impl.getMethodTable());
        forEachObjectArray(info, action);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void forEachObjectArray(CodeInfo info, NonmovableArrayAction action) {
        CodeInfoImpl impl = cast(info);
        action.apply(impl.getObjectFields());
        action.apply(impl.getObjectConstants());
        action.apply(impl.getClasses());
        action.apply(impl.getMemberNames());
        action.apply(impl.getOtherStrings());
        action.apply(impl.getDeoptimizationObjectConstants());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CodeInfoImpl cast(CodeInfo info) {
        assert CodeInfoAccess.isValid(info);
        return (CodeInfoImpl) info;
    }
}
