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

// Checkstyle: allow reflection

import java.lang.ref.WeakReference;

import org.graalvm.nativeimage.ImageSingletons;
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
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.jdk.Target_jdk_internal_ref_Cleaner;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.os.CommittedMemoryProvider;

public final class RuntimeMethodInfoAccess {
    private RuntimeMethodInfoAccess() {
    }

    static SubstrateInstalledCode getInstalledCode(CodeInfo info) {
        return CodeInfoAccess.<WeakReference<SubstrateInstalledCode>> getObjectField(info, CodeInfo.INSTALLEDCODE_OBJFIELD).get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static NonmovableArray<InstalledCodeObserverHandle> getCodeObserverHandles(CodeInfo info) {
        return info.getCodeObserverHandles();
    }

    public static void initialize(CodeInfo info, Pointer start, int size, int tier, NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        info.setCodeStart((CodePointer) start);
        info.setCodeSize(WordFactory.unsigned(size));
        info.setTier(tier);
        info.setCodeObserverHandles(observerHandles);
    }

    public static void setCodeObjectConstantsInfo(CodeInfo info, NonmovableArray<Byte> refMapEncoding, long refMapIndex) {
        assert info.getCodeStart().isNonNull();
        info.setObjectsReferenceMapEncoding(refMapEncoding);
        info.setObjectsReferenceMapIndex(refMapIndex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void setCodeConstantsLive(CodeInfo info) {
        assert !info.getCodeConstantsLive();
        info.setCodeConstantsLive(true);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    static void setDeoptimizationMetadata(CodeInfo info, NonmovableArray<Integer> startOffsets, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants) {
        info.setDeoptimizationStartOffsets(startOffsets);
        info.setDeoptimizationEncodings(encodings);
        info.setDeoptimizationObjectConstants(objectConstants);
    }

    public static void beforeInstallInCurrentIsolate(CodeInfo info, SubstrateInstalledCode installedCode) {
        assert info.getObjectFields().isNull();
        NonmovableObjectArray<Object> objectFields = NonmovableArrays.createObjectArray(CodeInfo.OBJFIELDS_COUNT);
        info.setObjectFields(objectFields); // must be first to ensure references are visible to GC
        Object tether = CodeInfoAccess.haveAssertions() ? new UninterruptibleUtils.AtomicInteger(0) : new Object();
        NonmovableArrays.setObject(objectFields, CodeInfo.TETHER_OBJFIELD, tether);
        NonmovableArrays.setObject(objectFields, CodeInfo.NAME_OBJFIELD, installedCode.getName());
        NonmovableArrays.setObject(objectFields, CodeInfo.INSTALLEDCODE_OBJFIELD, new WeakReference<>(installedCode));
        createCleaner(info, tether);
    }

    static void walkReferences(CodeInfo info, ObjectReferenceVisitor visitor) {
        NonmovableArrays.walkUnmanagedObjectArray(info.getObjectFields(), visitor);
        if (info.getCodeConstantsLive()) {
            CodeReferenceMapDecoder.walkOffsetsFromPointer(info.getCodeStart(), info.getObjectsReferenceMapEncoding(), info.getObjectsReferenceMapIndex(), visitor);
        }
        NonmovableArrays.walkUnmanagedObjectArray(info.getFrameInfoObjectConstants(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(info.getFrameInfoSourceClasses(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(info.getFrameInfoSourceMethodNames(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(info.getFrameInfoNames(), visitor);
        NonmovableArrays.walkUnmanagedObjectArray(info.getDeoptimizationObjectConstants(), visitor);
    }

    public static CodeInfo allocateMethodInfo() {
        CodeInfo info = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(SizeOf.unsigned(CodeInfo.class));
        RuntimeMethodInfoMemory.singleton().add(info);
        return info;
    }

    private static void createCleaner(CodeInfo info, Object tether) {
        Target_jdk_internal_ref_Cleaner.create(tether, new RuntimeMethodInfoCleaner(info));
    }

    private static final class RuntimeMethodInfoCleaner implements Runnable {
        private final CodeInfo codeInfo;

        private RuntimeMethodInfoCleaner(CodeInfo codeInfo) {
            this.codeInfo = codeInfo;
        }

        @Override
        public void run() {
            boolean removed = RuntimeMethodInfoMemory.singleton().remove(codeInfo);
            assert removed : "must have been present";
            releaseMethodInfoMemory(codeInfo);
        }
    }

    static void partialReleaseAfterInvalidate(CodeInfo info) {
        assert NonmovableArrays.getObject(info.getObjectFields(), CodeInfo.TETHER_OBJFIELD) != null : "already released";

        InstalledCodeObserverSupport.removeObservers(RuntimeMethodInfoAccess.getCodeObserverHandles(info));
        NonmovableArrays.releaseUnmanagedArray(info.getCodeObserverHandles());
        info.setCodeObserverHandles(NonmovableArrays.nullArray());

        info.setCodeConstantsLive(false);
        releaseCodeMemory(info.getCodeStart(), info.getCodeSize());

        /*
         * Set our reference to the tether object to null so that the Cleaner object can free our
         * memory as soon as any other references, e.g. from ongoing stack walks, are gone.
         */
        NonmovableArrays.setObject(info.getObjectFields(), CodeInfo.TETHER_OBJFIELD, null);
    }

    public static CodePointer allocateCodeMemory(UnsignedWord size) {
        return (CodePointer) CommittedMemoryProvider.get().allocate(size, WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
    }

    public static void releaseCodeMemory(CodePointer codeStart, UnsignedWord codeSize) {
        CommittedMemoryProvider.get().free(codeStart, codeSize, WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    static void releaseMethodInfoOnTearDown(CodeInfo info) {
        InstalledCodeObserverSupport.removeObserversOnTearDown(getCodeObserverHandles(info));
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
    private static void releaseMethodInfoMemory(CodeInfo info) {
        forEachArray(info, RELEASE_ACTION);
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void forEachArray(CodeInfo info, NonmovableArrayAction action) {
        action.apply(info.getCodeInfoIndex());
        action.apply(info.getCodeInfoEncodings());
        action.apply(info.getReferenceMapEncoding());
        action.apply(info.getFrameInfoEncodings());
        action.apply(info.getDeoptimizationStartOffsets());
        action.apply(info.getDeoptimizationEncodings());
        action.apply(info.getObjectsReferenceMapEncoding());
        action.apply(info.getCodeObserverHandles());
        forEachObjectArray(info, action);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void forEachObjectArray(CodeInfo info, NonmovableArrayAction action) {
        action.apply(info.getObjectFields());
        action.apply(info.getFrameInfoObjectConstants());
        action.apply(info.getFrameInfoSourceClasses());
        action.apply(info.getFrameInfoSourceMethodNames());
        action.apply(info.getFrameInfoNames());
        action.apply(info.getDeoptimizationObjectConstants());
    }
}
