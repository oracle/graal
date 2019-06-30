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
import java.lang.reflect.Method;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.c.UnmanagedReferenceWalkers;
import com.oracle.svm.core.c.UnmanagedReferenceWalkers.UnmanagedObjectReferenceWalker;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.VMError;

public final class RuntimeMethodInfoAccess {
    /** Reference walker. A constant field so we always use the exact same object, do not inline! */
    private static final UnmanagedObjectReferenceWalker REFERENCE_WALKER = RuntimeMethodInfoAccess::walkReferences;

    private RuntimeMethodInfoAccess() {
    }

    static SubstrateInstalledCode getInstalledCode(CodeInfo info) {
        return CodeInfoAccess.<WeakReference<SubstrateInstalledCode>> getObjectField(info, 2).get();
    }

    static InstalledCodeObserverHandle[] getCodeObserverHandles(CodeInfo info) {
        return CodeInfoAccess.getObjectField(info, 3);
    }

    public static void setCodeLocation(CodeInfo info, Pointer start, int size) {
        info.setCodeStart((CodePointer) start);
        info.setCodeSize(WordFactory.unsigned(size));
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

    public static void setData(CodeInfo info, SubstrateInstalledCode installedCode, int tier, InstalledCodeObserverHandle[] codeObserverHandles) {
        assert codeObserverHandles != null;
        NonmovableObjectArray<Object> objectFields = info.getObjectFields();
        NonmovableArrays.setObject(objectFields, 1, installedCode.getName());
        NonmovableArrays.setObject(objectFields, 2, new WeakReference<>(installedCode));
        NonmovableArrays.setObject(objectFields, 3, codeObserverHandles);
        info.setTier(tier);
    }

    private static void walkReferences(ComparableWord tag, ObjectReferenceVisitor visitor) {
        CodeInfo info = (CodeInfo) tag;
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
        CodeInfo info = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(WordFactory.unsigned(SizeOf.get(CodeInfo.class)));
        UnmanagedReferenceWalkers.singleton().register(REFERENCE_WALKER, info);
        NonmovableObjectArray<Object> objectFields = NonmovableArrays.createObjectArray(4);
        Object obj = CodeInfoAccess.haveAssertions() ? new UninterruptibleUtils.AtomicInteger(0) : new Object();
        NonmovableArrays.setObject(objectFields, 0, obj);
        info.setObjectFields(objectFields);
        createCleaner(info, obj);
        return info;
    }

    private static final Method CLEANER_CREATE_METHOD;
    static {
        try { /* Cleaner class moved after JDK 8 */
            // Checkstyle: stop
            Class<?> clazz = (JavaVersionUtil.JAVA_SPEC <= 8) ? Class.forName("sun.misc.Cleaner") : Class.forName("jdk.internal.ref.Cleaner");
            // Checkstyle: resume
            CLEANER_CREATE_METHOD = clazz.getDeclaredMethod("create", Object.class, Runnable.class);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static void createCleaner(CodeInfo info, Object obj) {
        try {
            CLEANER_CREATE_METHOD.invoke(null, obj, new RuntimeMethodInfoCleaner(info));
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static final class RuntimeMethodInfoCleaner implements Runnable {
        private final CodeInfo codeInfo;

        private RuntimeMethodInfoCleaner(CodeInfo codeInfo) {
            this.codeInfo = codeInfo;
        }

        @Override
        public void run() {
            boolean unregistered = UnmanagedReferenceWalkers.singleton().unregister(REFERENCE_WALKER, codeInfo);
            assert unregistered : "must have been present";
            releaseMethodInfoMemory(codeInfo);
        }
    }

    static void releaseInstalledCodeAndTether(CodeInfo info) {
        assert NonmovableArrays.getObject(info.getObjectFields(), 0) != null : "already released";

        info.setCodeConstantsLive(false);
        releaseInstalledCode(info);

        /*
         * Set our reference to the tether object to null so that the Cleaner object can free our
         * memory as soon as any other references, e.g. from ongoing stack walks, are gone.
         */
        NonmovableArrays.setObject(info.getObjectFields(), 0, null);
    }

    private static void releaseInstalledCode(CodeInfo codeInfo) {
        CommittedMemoryProvider.get().free(codeInfo.getCodeStart(), codeInfo.getCodeSize(), CommittedMemoryProvider.UNALIGNED, true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    static void releaseMethodInfoOnTearDown(CodeInfo info) {
        // Don't bother with the reference walker on tear-down, this is handled elsewhere
        releaseMethodInfoMemory(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    private static void releaseMethodInfoMemory(CodeInfo info) {
        NonmovableArrays.releaseUnmanagedArray(info.getObjectFields());
        NonmovableArrays.releaseUnmanagedArray(info.getCodeInfoIndex());
        NonmovableArrays.releaseUnmanagedArray(info.getCodeInfoEncodings());
        NonmovableArrays.releaseUnmanagedArray(info.getReferenceMapEncoding());
        NonmovableArrays.releaseUnmanagedArray(info.getFrameInfoEncodings());
        NonmovableArrays.releaseUnmanagedArray(info.getFrameInfoObjectConstants());
        NonmovableArrays.releaseUnmanagedArray(info.getFrameInfoSourceClasses());
        NonmovableArrays.releaseUnmanagedArray(info.getFrameInfoSourceMethodNames());
        NonmovableArrays.releaseUnmanagedArray(info.getFrameInfoNames());
        NonmovableArrays.releaseUnmanagedArray(info.getDeoptimizationStartOffsets());
        NonmovableArrays.releaseUnmanagedArray(info.getDeoptimizationEncodings());
        NonmovableArrays.releaseUnmanagedArray(info.getDeoptimizationObjectConstants());
        NonmovableArrays.releaseUnmanagedArray(info.getObjectsReferenceMapEncoding());

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(info);
    }
}
