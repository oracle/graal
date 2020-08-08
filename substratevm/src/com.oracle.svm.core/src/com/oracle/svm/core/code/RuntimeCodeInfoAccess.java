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

import java.util.EnumSet;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.os.CommittedMemoryProvider;

/**
 * This class contains methods that only make sense for runtime compiled code.
 */
public final class RuntimeCodeInfoAccess {
    private RuntimeCodeInfoAccess() {
    }

    public static SubstrateInstalledCode getInstalledCode(CodeInfo info) {
        return CodeInfoAccess.<SubstrateInstalledCode> getObjectField(info, CodeInfoImpl.INSTALLEDCODE_OBJFIELD);
    }

    public static void setInstalledCode(CodeInfo info, SubstrateInstalledCode installedCode) {
        NonmovableObjectArray<Object> objectFields = cast(info).getObjectFields();
        NonmovableArrays.setObject(objectFields, CodeInfoImpl.INSTALLEDCODE_OBJFIELD, installedCode);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<InstalledCodeObserverHandle> getCodeObserverHandles(CodeInfo info) {
        return cast(info).getCodeObserverHandles();
    }

    public static void initialize(CodeInfo info, Pointer start, int size, int tier, NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        CodeInfoImpl impl = cast(info);
        impl.setCodeStart((CodePointer) start);
        impl.setCodeSize(WordFactory.unsigned(size));
        impl.setTier(tier);
        impl.setCodeObserverHandles(observerHandles);
    }

    public static void setCodeObjectConstantsInfo(CodeInfo info, NonmovableArray<Byte> refMapEncoding, long refMapIndex) {
        CodeInfoImpl impl = cast(info);
        assert impl.getCodeStart().isNonNull();
        impl.setObjectsReferenceMapEncoding(refMapEncoding);
        impl.setObjectsReferenceMapIndex(refMapIndex);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    static void setDeoptimizationMetadata(CodeInfo info, NonmovableArray<Integer> startOffsets, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants) {
        CodeInfoImpl impl = cast(info);
        impl.setDeoptimizationStartOffsets(startOffsets);
        impl.setDeoptimizationEncodings(encodings);
        impl.setDeoptimizationObjectConstants(objectConstants);
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
        // after setting all the object data , notify the GC
        Heap.getHeap().registerRuntimeCodeInfo(info);
    }

    /**
     * Walks all strong references in a {@link CodeInfo} object.
     */
    public static boolean walkStrongReferences(CodeInfo info, ObjectReferenceVisitor visitor) {
        return NonmovableArrays.walkUnmanagedObjectArray(cast(info).getObjectFields(), visitor, CodeInfoImpl.FIRST_STRONGLY_REFERENCED_OBJFIELD, CodeInfoImpl.STRONGLY_REFERENCED_OBJFIELD_COUNT);
    }

    /**
     * Walks all weak references in a {@link CodeInfo} object.
     */
    public static boolean walkWeakReferences(CodeInfo info, ObjectReferenceVisitor visitor) {
        CodeInfoImpl impl = cast(info);
        boolean continueVisiting = true;
        continueVisiting = continueVisiting &&
                        NonmovableArrays.walkUnmanagedObjectArray(impl.getObjectFields(), visitor, CodeInfoImpl.FIRST_WEAKLY_REFERENCED_OBJFIELD, CodeInfoImpl.WEAKLY_REFERENCED_OBJFIELD_COUNT);
        if (impl.getState() == CodeInfo.STATE_CODE_CONSTANTS_LIVE) {
            continueVisiting = continueVisiting && CodeReferenceMapDecoder.walkOffsetsFromPointer(impl.getCodeStart(),
                            impl.getObjectsReferenceMapEncoding(), impl.getObjectsReferenceMapIndex(), visitor);
        }
        continueVisiting = continueVisiting && NonmovableArrays.walkUnmanagedObjectArray(impl.getFrameInfoObjectConstants(), visitor);
        continueVisiting = continueVisiting && NonmovableArrays.walkUnmanagedObjectArray(impl.getFrameInfoSourceClasses(), visitor);
        continueVisiting = continueVisiting && NonmovableArrays.walkUnmanagedObjectArray(impl.getFrameInfoSourceMethodNames(), visitor);
        continueVisiting = continueVisiting && NonmovableArrays.walkUnmanagedObjectArray(impl.getFrameInfoNames(), visitor);
        continueVisiting = continueVisiting && NonmovableArrays.walkUnmanagedObjectArray(impl.getDeoptimizationObjectConstants(), visitor);
        return continueVisiting;
    }

    /**
     * This method only visits a subset of all the references, so you typically wan't to use
     * {@link #walkStrongReferences} and/or {@link #walkWeakReferences} instead.
     */
    public static boolean walkObjectFields(CodeInfo info, ObjectReferenceVisitor visitor) {
        return NonmovableArrays.walkUnmanagedObjectArray(cast(info).getObjectFields(), visitor);
    }

    public static CodeInfo allocateMethodInfo() {
        CodeInfoImpl info = UnmanagedMemory.calloc(SizeOf.unsigned(CodeInfoImpl.class));
        NonmovableObjectArray<Object> objectFields = NonmovableArrays.createObjectArray(CodeInfoImpl.OBJFIELDS_COUNT);
        info.setObjectFields(objectFields);

        // Make the object visible to the GC (before writing any heap data into the object).
        RuntimeCodeInfoMemory.singleton().add(info);
        return info;
    }

    static void partialReleaseAfterInvalidate(CodeInfo info) {
        InstalledCodeObserverSupport.removeObservers(RuntimeCodeInfoAccess.getCodeObserverHandles(info));
        releaseMemory(info);
    }

    @Uninterruptible(reason = "Prevent the GC from running - otherwise, it could accidentally visit the freed memory.")
    private static void releaseMemory(CodeInfo info) {
        CodeInfoImpl impl = cast(info);
        assert impl.getState() == CodeInfo.STATE_CODE_CONSTANTS_LIVE || impl.getState() == CodeInfo.STATE_READY_FOR_INVALIDATION : "unexpected state (probably already released)";
        // Notify the GC as long as the object data is still valid.
        Heap.getHeap().unregisterCodeConstants(info);

        NonmovableArrays.releaseUnmanagedArray(impl.getCodeObserverHandles());
        impl.setCodeObserverHandles(NonmovableArrays.nullArray());

        releaseCodeMemory(impl.getCodeStart(), impl.getCodeSize());
        /*
         * Note that we must not null-out any CodeInfo metadata as it can be accessed in a stack
         * walk even when CodeInfo data is already partially freed.
         */
        CodeInfoAccess.setState(info, CodeInfo.STATE_PARTIALLY_FREED);
    }

    public static CodePointer allocateCodeMemory(UnsignedWord size) {
        return (CodePointer) CommittedMemoryProvider.get().allocate(size, WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void releaseCodeMemory(CodePointer codeStart, UnsignedWord codeSize) {
        CommittedMemoryProvider.get().free(codeStart, codeSize, WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
    }

    public static void makeCodeMemoryExecutableReadOnly(CodePointer codeStart, UnsignedWord codeSize) {
        CommittedMemoryProvider.get().protect(codeStart, codeSize, EnumSet.of(CommittedMemoryProvider.Access.READ, CommittedMemoryProvider.Access.EXECUTE));
    }

    public static void makeCodeMemoryWriteableNonExecutable(CodePointer start, UnsignedWord size) {
        CommittedMemoryProvider.get().protect(start, size, EnumSet.of(CommittedMemoryProvider.Access.READ, CommittedMemoryProvider.Access.WRITE));
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    static void releaseMethodInfoOnTearDown(CodeInfo info) {
        InstalledCodeObserverSupport.removeObserversOnTearDown(getCodeObserverHandles(info));

        assert ((CodeInfoTether) UntetheredCodeInfoAccess.getTetherUnsafe(info)).getCount() == 1 : "CodeInfo tether must not be referenced by non-teardown code.";
        releaseMethodInfoMemory(info);
    }

    public interface NonmovableArrayAction {
        @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
        void apply(NonmovableArray<?> array);
    }

    private static final NonmovableArrayAction RELEASE_ACTION = new NonmovableArrayAction() {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
        public void apply(NonmovableArray<?> array) {
            NonmovableArrays.releaseUnmanagedArray(array);
        }
    };

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void releaseMethodInfoMemory(CodeInfo info) {
        // Notify the GC as long as the object data is still valid.
        Heap.getHeap().unregisterRuntimeCodeInfo(info);

        forEachArray(info, RELEASE_ACTION);
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void forEachArray(CodeInfo info, NonmovableArrayAction action) {
        CodeInfoImpl impl = cast(info);
        action.apply(impl.getCodeInfoIndex());
        action.apply(impl.getCodeInfoEncodings());
        action.apply(impl.getReferenceMapEncoding());
        action.apply(impl.getFrameInfoEncodings());
        action.apply(impl.getDeoptimizationStartOffsets());
        action.apply(impl.getDeoptimizationEncodings());
        action.apply(impl.getObjectsReferenceMapEncoding());
        action.apply(impl.getCodeObserverHandles());
        forEachObjectArray(info, action);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void forEachObjectArray(CodeInfo info, NonmovableArrayAction action) {
        CodeInfoImpl impl = cast(info);
        action.apply(impl.getObjectFields());
        action.apply(impl.getFrameInfoObjectConstants());
        action.apply(impl.getFrameInfoSourceClasses());
        action.apply(impl.getFrameInfoSourceMethodNames());
        action.apply(impl.getFrameInfoNames());
        action.apply(impl.getDeoptimizationObjectConstants());
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    private static CodeInfoImpl cast(CodeInfo info) {
        assert CodeInfoAccess.isValid(info);
        return (CodeInfoImpl) info;
    }
}
