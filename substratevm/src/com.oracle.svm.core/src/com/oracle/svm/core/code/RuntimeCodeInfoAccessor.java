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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
<<<<<<< HEAD
=======
import com.oracle.svm.core.annotate.NeverInline;
>>>>>>> 885e76ca2a5... Support retaining code information with a Cleaner object.
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.c.UnmanagedReferenceWalkers;
import com.oracle.svm.core.c.function.JavaMethodLiteral;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.VMError;

public class RuntimeCodeInfoAccessor implements CodeInfoAccessor {
    @Fold
    static boolean haveAssertions() {
        return SubstrateOptions.getRuntimeAssertionsForClass(RuntimeCodeInfoAccessor.class.getName());
    }

    public static final CodeInfoHandle NULL_HANDLE = null;

    public static final JavaMethodLiteral<UnmanagedReferenceWalkers.ObjectReferenceWalkerFunction> walkReferencesFunction = JavaMethodLiteral.create(
                    RuntimeCodeInfoAccessor.class, "walkReferences", ComparableWord.class, ObjectReferenceVisitor.class);

    private final RuntimeCodeInfo codeCache;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static RuntimeMethodInfo cast(CodeInfoHandle handle) {
        return (RuntimeMethodInfo) handle;
    }

    RuntimeCodeInfoAccessor(RuntimeCodeInfo codeCache) {
        this.codeCache = codeCache;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodeInfoHandle lookupCodeInfo(CodePointer ip) {
        return codeCache.lookupMethod(ip);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Object acquireTether(CodeInfoHandle handle) {
        Object tether = getObjectField(handle, 0);
        assert ((UninterruptibleUtils.AtomicInteger) tether).incrementAndGet() > 0;
        return tether;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isTethered(CodeInfoHandle handle) {
        return !haveAssertions() || ((UninterruptibleUtils.AtomicInteger) getObjectField(handle, 0)).get() > 0;
    }

    @Override
    @NeverInline("Prevent elimination of object reference in caller.")
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void releaseTether(CodeInfoHandle handle, Object tether) {
        assert tether == getObjectField(handle, 0) || getObjectField(handle, 0) == null;
        assert ((UninterruptibleUtils.AtomicInteger) tether).getAndDecrement() > 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isNone(CodeInfoHandle handle) {
        return handle.rawValue() == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getTier(CodeInfoHandle handle) {
        return cast(handle).getTier();
    }

    NonmovableArray<Integer> getDeoptimizationStartOffsets(CodeInfoHandle handle) {
        return cast(handle).getDeoptimizationStartOffsets();
    }

    NonmovableArray<Byte> getDeoptimizationEncodings(CodeInfoHandle handle) {
        return cast(handle).getDeoptimizationEncodings();
    }

    NonmovableObjectArray<Object> getDeoptimizationObjectConstants(CodeInfoHandle handle) {
        return cast(handle).getDeoptimizationObjectConstants();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public CodePointer getCodeStart(CodeInfoHandle handle) {
        return cast(handle).getCodeStart();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getCodeSize(CodeInfoHandle handle) {
        return cast(handle).getCodeSize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    CodePointer getCodeEnd(CodeInfoHandle handle) {
        return (CodePointer) ((UnsignedWord) cast(handle).getCodeStart()).add(cast(handle).getCodeSize());
    }

    @Override
    public boolean contains(CodeInfoHandle handle, CodePointer ip) {
        return CodeInfoAccessor.contains(cast(handle).getCodeStart(), cast(handle).getCodeSize(), ip);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long relativeIP(CodeInfoHandle handle, CodePointer ip) {
        return CodeInfoAccessor.relativeIP(cast(handle).getCodeStart(), cast(handle).getCodeSize(), ip);
    }

    @Override
    public CodePointer absoluteIP(CodeInfoHandle handle, long relativeIP) {
        return CodeInfoAccessor.absoluteIP(cast(handle).getCodeStart(), relativeIP);
    }

    @Override
    public long initFrameInfoReader(CodeInfoHandle handle, CodePointer ip, ReusableTypeReader frameInfoReader) {
        return CodeInfoAccessor.initFrameInfoReader(cast(handle).getCodeInfoEncodings(), cast(handle).getCodeInfoIndex(),
                        cast(handle).getFrameInfoEncodings(), relativeIP(handle, ip), frameInfoReader);
    }

    @Override
    public FrameInfoQueryResult nextFrameInfo(CodeInfoHandle handle, long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoDecoder.FrameInfoQueryResultAllocator resultAllocator, FrameInfoDecoder.ValueInfoAllocator valueInfoAllocator, boolean fetchFirstFrame) {
        return CodeInfoAccessor.nextFrameInfo(cast(handle).getCodeInfoEncodings(), cast(handle).getFrameInfoNames(), cast(handle).getFrameInfoObjectConstants(),
                        cast(handle).getFrameInfoSourceClasses(), cast(handle).getFrameInfoSourceMethodNames(), entryOffset, frameInfoReader, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T> T getObjectField(CodeInfoHandle handle, int index) {
        return (T) NonmovableArrays.getObject(cast(handle).getObjectFields(), index);
    }

    @Override
    public String getName(CodeInfoHandle handle) {
        return getObjectField(handle, 1);
    }

    SubstrateInstalledCode getInstalledCode(CodeInfoHandle handle) {
        return RuntimeCodeInfoAccessor.<WeakReference<SubstrateInstalledCode>> getObjectField(handle, 2).get();
    }

    InstalledCodeObserverHandle[] getCodeObserverHandles(CodeInfoHandle handle) {
        return getObjectField(handle, 3);
    }

    @Override
    public long lookupDeoptimizationEntrypoint(CodeInfoHandle handle, long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(cast(handle).getCodeInfoEncodings(), cast(handle).getCodeInfoIndex(),
                        cast(handle).getFrameInfoEncodings(), cast(handle).getFrameInfoNames(), cast(handle).getFrameInfoObjectConstants(),
                        cast(handle).getFrameInfoSourceClasses(), cast(handle).getFrameInfoSourceMethodNames(), method, encodedBci, codeInfo);
    }

    @Override
    public long lookupTotalFrameSize(CodeInfoHandle handle, long ip) {
        return CodeInfoDecoder.lookupTotalFrameSize(cast(handle).getCodeInfoEncodings(), cast(handle).getCodeInfoIndex(), ip);
    }

    @Override
    public long lookupExceptionOffset(CodeInfoHandle handle, long ip) {
        return CodeInfoDecoder.lookupExceptionOffset(cast(handle).getCodeInfoEncodings(), cast(handle).getCodeInfoIndex(), ip);
    }

    @Override
    public NonmovableArray<Byte> getReferenceMapEncoding(CodeInfoHandle handle) {
        return cast(handle).getReferenceMapEncoding();
    }

    @Override
    public long lookupReferenceMapIndex(CodeInfoHandle handle, long ip) {
        return CodeInfoDecoder.lookupReferenceMapIndex(cast(handle).getCodeInfoEncodings(), cast(handle).getCodeInfoIndex(), ip);
    }

    @Override
    public void lookupCodeInfo(CodeInfoHandle handle, long ip, CodeInfoQueryResult codeInfo) {
        CodeInfoDecoder.lookupCodeInfo(cast(handle).getCodeInfoEncodings(), cast(handle).getCodeInfoIndex(), cast(handle).getFrameInfoEncodings(), cast(handle).getFrameInfoNames(),
                        cast(handle).getFrameInfoObjectConstants(), cast(handle).getFrameInfoSourceClasses(), cast(handle).getFrameInfoSourceMethodNames(), ip, codeInfo);
    }

    @Override
    public void setCodeInfo(CodeInfoHandle handle, NonmovableArray<Byte> index, NonmovableArray<Byte> encodings, NonmovableArray<Byte> referenceMapEncoding) {
        cast(handle).setCodeInfoIndex(index);
        cast(handle).setCodeInfoEncodings(encodings);
        cast(handle).setReferenceMapEncoding(referenceMapEncoding);
    }

    @Override
    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    public void setFrameInfo(CodeInfoHandle handle, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants,
                    NonmovableObjectArray<Class<?>> sourceClasses, NonmovableObjectArray<String> sourceMethodNames, NonmovableObjectArray<String> names) {

        cast(handle).setFrameInfoEncodings(encodings);
        cast(handle).setFrameInfoObjectConstants(objectConstants);
        cast(handle).setFrameInfoSourceClasses(sourceClasses);
        cast(handle).setFrameInfoSourceMethodNames(sourceMethodNames);
        cast(handle).setFrameInfoNames(names);
    }

    @Override
    public Log log(CodeInfoHandle handle, Log log) {
        return isNone(handle) ? log.string("null") : log.string(RuntimeCodeInfo.class.getName()).string("@").hex(handle);
    }

    public void setCodeLocation(CodeInfoHandle handle, Pointer start, int size) {
        cast(handle).setCodeStart((CodePointer) start);
        cast(handle).setCodeSize(WordFactory.unsigned(size));
    }

    public void setCodeObjectConstantsInfo(CodeInfoHandle handle, NonmovableArray<Byte> refMapEncoding, long refMapIndex) {
        assert cast(handle).getCodeStart().isNonNull();
        cast(handle).setObjectsReferenceMapEncoding(refMapEncoding);
        cast(handle).setObjectsReferenceMapIndex(refMapIndex);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public void setCodeConstantsLive(CodeInfoHandle handle) {
        assert !cast(handle).getCodeConstantsLive();
        cast(handle).setCodeConstantsLive(true);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    void setDeoptimizationMetadata(CodeInfoHandle handle, NonmovableArray<Integer> startOffsets, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants) {
        cast(handle).setDeoptimizationStartOffsets(startOffsets);
        cast(handle).setDeoptimizationEncodings(encodings);
        cast(handle).setDeoptimizationObjectConstants(objectConstants);
    }

    public void setData(CodeInfoHandle handle, SubstrateInstalledCode installedCode, int tier, InstalledCodeObserverHandle[] codeObserverHandles) {
        assert codeObserverHandles != null;
        NonmovableObjectArray<Object> objectFields = cast(handle).getObjectFields();
        NonmovableArrays.setObject(objectFields, 1, installedCode.getName());
        NonmovableArrays.setObject(objectFields, 2, new WeakReference<>(installedCode));
        NonmovableArrays.setObject(objectFields, 3, codeObserverHandles);
        cast(handle).setTier(tier);
    }

    @SuppressWarnings("unused")
    static void walkReferences(ComparableWord handle, ObjectReferenceVisitor visitor) {
        RuntimeMethodInfo info = cast((CodeInfoHandle) handle);
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

    public static CodeInfoHandle allocateMethodInfo() {
        CodeInfoHandle handle = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(WordFactory.unsigned(SizeOf.get(RuntimeMethodInfo.class)));
        UnmanagedReferenceWalkers.singleton().register(walkReferencesFunction.getFunctionPointer(), handle);
        NonmovableObjectArray<Object> objectFields = NonmovableArrays.createObjectArray(4);
        Object obj = haveAssertions() ? new UninterruptibleUtils.AtomicInteger(0) : new Object();
        NonmovableArrays.setObject(objectFields, 0, obj);
        cast(handle).setObjectFields(objectFields);
        createCleaner(handle, obj);
        return handle;
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

    private static void createCleaner(CodeInfoHandle handle, Object obj) {
        try {
            CLEANER_CREATE_METHOD.invoke(null, obj, new RuntimeMethodInfoCleaner(handle));
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static final class RuntimeMethodInfoCleaner implements Runnable {
        private final CodeInfoHandle handle;

        private RuntimeMethodInfoCleaner(CodeInfoHandle handle) {
            this.handle = handle;
        }

        @Override
        public void run() {
            boolean unregistered = UnmanagedReferenceWalkers.singleton().unregister(walkReferencesFunction.getFunctionPointer(), handle);
            assert unregistered : "must have been present";
            releaseMethodInfoMemory(handle);
        }
    }

    UnsignedWord getMetadataCodeSize(CodeInfoHandle handle) {
        RuntimeMethodInfo info = cast(handle);
        return SizeOf.unsigned(RuntimeMethodInfo.class)
                        .add(NonmovableArrays.byteSizeOf(info.getObjectFields()))
                        .add(NonmovableArrays.byteSizeOf(info.getCodeInfoIndex()))
                        .add(NonmovableArrays.byteSizeOf(info.getCodeInfoEncodings()))
                        .add(NonmovableArrays.byteSizeOf(info.getReferenceMapEncoding()))
                        .add(NonmovableArrays.byteSizeOf(info.getFrameInfoEncodings()))
                        .add(NonmovableArrays.byteSizeOf(info.getFrameInfoObjectConstants()))
                        .add(NonmovableArrays.byteSizeOf(info.getFrameInfoSourceClasses()))
                        .add(NonmovableArrays.byteSizeOf(info.getFrameInfoSourceMethodNames()))
                        .add(NonmovableArrays.byteSizeOf(info.getFrameInfoNames()))
                        .add(NonmovableArrays.byteSizeOf(info.getDeoptimizationStartOffsets()))
                        .add(NonmovableArrays.byteSizeOf(info.getDeoptimizationEncodings()))
                        .add(NonmovableArrays.byteSizeOf(info.getDeoptimizationObjectConstants()))
                        .add(NonmovableArrays.byteSizeOf(info.getObjectsReferenceMapEncoding()));
    }

    void releaseInstalledCodeAndTether(CodeInfoHandle handle) {
        assert NonmovableArrays.getObject(cast(handle).getObjectFields(), 0) != null : "already released";

        cast(handle).setCodeConstantsLive(false);
        releaseInstalledCode(cast(handle));

        /*
         * Set our reference to the tether object to null so that the Cleaner object can free our
         * memory as soon as any other references, e.g. from ongoing stack walks, are gone.
         */
        NonmovableArrays.setObject(cast(handle).getObjectFields(), 0, null);
    }

    private static void releaseInstalledCode(RuntimeMethodInfo runtimeMethodInfo) {
        CommittedMemoryProvider.get().free(runtimeMethodInfo.getCodeStart(), runtimeMethodInfo.getCodeSize(), WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    void releaseMethodInfoOnTearDown(CodeInfoHandle handle) {
        // Don't bother with the reference walker on tear-down, this is handled elsewhere
        releaseMethodInfoMemory(handle);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    private static void releaseMethodInfoMemory(CodeInfoHandle handle) {
        RuntimeMethodInfo info = cast(handle);
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
