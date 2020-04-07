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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Provides functionality to query information about a unit of compiled code from a {@link CodeInfo}
 * object. This helper class is necessary to ensure that {@link CodeInfo} objects are used
 * correctly, as they are garbage collected even though they live in unmanaged memory. For that
 * purpose, every {@link CodeInfo} object has a tether object. The garbage collector can free a
 * {@link CodeInfo} object if its tether object is unreachable at a safepoint, that is, in any
 * method that is not annotated with {@link Uninterruptible}.
 * <p>
 * For better type-safety (i.e., to indicate if the tether of a {@link CodeInfo} object was already
 * acquired), we distinguish between {@link UntetheredCodeInfo} and {@link CodeInfo}.
 * <p>
 * {@link UntetheredCodeInfo} objects could be freed at any safepoint. To prevent that, it is
 * possible to call {@link #acquireTether(UntetheredCodeInfo)} from uninterruptible code to create a
 * strong reference to the tether object, which prevents the garbage collector from freeing the
 * object. Subsequently, the {@link UntetheredCodeInfo} should be
 * {@link #convert(UntetheredCodeInfo, Object) converted} to a {@link CodeInfo} object.
 * <p>
 * {@link CodeInfo} objects can be safely passed to interruptible code as their tether was already
 * acquired (calling a separate method with {@link Uninterruptible#calleeMustBe()} == false is
 * recommended). When no further data needs to be accessed, the tether must be
 * {@link #releaseTether(UntetheredCodeInfo, Object) released}. For concrete code examples, see the
 * usages of these methods.
 * <p>
 * Callers of these methods must take into account that frames on the stack can be deoptimized at
 * any safepoint check. When a method is deoptimized, its code can also be
 * {@linkplain CodeInfoTable#invalidateInstalledCode invalidated}, successive
 * {@linkplain CodeInfoTable#lookupCodeInfo lookups of instruction pointers} within the deoptimized
 * code will fail. So, only the initially looked up {@link CodeInfo} object must be used.
 */
public final class CodeInfoAccess {
    private CodeInfoAccess() {
    }

    @Fold
    static boolean haveAssertions() {
        return SubstrateOptions.getRuntimeAssertionsForClass(CodeInfoAccess.class.getName());
    }

    @Uninterruptible(reason = "The handle should only be accessed from uninterruptible code to prevent that the GC frees the CodeInfo.", callerMustBe = true)
    public static Object acquireTether(UntetheredCodeInfo info) {
        Object tether = UntetheredCodeInfoAccess.getTetherUnsafe(info);
        /*
         * Do not interact with the tether object during GCs, as the reference might be forwarded
         * and therefore not safe to access. Tethering is not needed then, either.
         */
        assert VMOperation.isGCInProgress() || ((CodeInfoTether) tether).incrementCount() > 0;
        return tether;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @NeverInline("Prevent elimination of object reference in caller.")
    public static void releaseTether(UntetheredCodeInfo info, Object tether) {
        assert VMOperation.isGCInProgress() || UntetheredCodeInfoAccess.getTetherUnsafe(info) == null || UntetheredCodeInfoAccess.getTetherUnsafe(info) == tether;
        assert VMOperation.isGCInProgress() || ((CodeInfoTether) tether).decrementCount() >= 0;
    }

    /**
     * Try to avoid using this method. It is similar to
     * {@link #releaseTether(UntetheredCodeInfo, Object)} but with less verification.
     */
    @Uninterruptible(reason = "Called during teardown.", callerMustBe = true)
    @NeverInline("Prevent elimination of object reference in caller.")
    public static void releaseTetherUnsafe(@SuppressWarnings("unused") UntetheredCodeInfo info, Object tether) {
        assert VMOperation.isGCInProgress() || ((CodeInfoTether) tether).decrementCount() >= 0;
    }

    @Uninterruptible(reason = "Should be called from the same method as acquireTether.", callerMustBe = true)
    public static CodeInfo convert(UntetheredCodeInfo untetheredInfo, Object tether) {
        assert UntetheredCodeInfoAccess.getTetherUnsafe(untetheredInfo) == null || UntetheredCodeInfoAccess.getTetherUnsafe(untetheredInfo) == tether;
        return convert(untetheredInfo);
    }

    /**
     * Try to avoid using this method. It is similar to {@link #convert(UntetheredCodeInfo, Object)}
     * but with less verification.
     */
    @Uninterruptible(reason = "Called by uninterruptible code.", mayBeInlined = true)
    public static CodeInfo convert(UntetheredCodeInfo untetheredInfo) {
        assert isValid(untetheredInfo);
        return (CodeInfo) untetheredInfo;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isValid(UntetheredCodeInfo info) {
        return SubstrateUtil.HOSTED || !haveAssertions() || VMOperation.isGCInProgress() || UntetheredCodeInfoAccess.getTetherUnsafe(info) == null ||
                        ((CodeInfoTether) UntetheredCodeInfoAccess.getTetherUnsafe(info)).getCount() > 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void setState(CodeInfo info, int state) {
        assert getState(info) < state;
        cast(info).setState(state);
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static int getState(CodeInfo info) {
        return cast(info).getState();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodePointer getCodeStart(CodeInfo info) {
        return cast(info).getCodeStart();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getCodeSize(CodeInfo info) {
        return cast(info).getCodeSize();
    }

    public static UnsignedWord getMetadataSize(CodeInfo info) {
        CodeInfoImpl impl = cast(info);
        return SizeOf.unsigned(CodeInfo.class)
                        .add(NonmovableArrays.byteSizeOf(impl.getObjectFields()))
                        .add(NonmovableArrays.byteSizeOf(impl.getCodeInfoIndex()))
                        .add(NonmovableArrays.byteSizeOf(impl.getCodeInfoEncodings()))
                        .add(NonmovableArrays.byteSizeOf(impl.getReferenceMapEncoding()))
                        .add(NonmovableArrays.byteSizeOf(impl.getFrameInfoEncodings()))
                        .add(NonmovableArrays.byteSizeOf(impl.getFrameInfoObjectConstants()))
                        .add(NonmovableArrays.byteSizeOf(impl.getFrameInfoSourceClasses()))
                        .add(NonmovableArrays.byteSizeOf(impl.getFrameInfoSourceMethodNames()))
                        .add(NonmovableArrays.byteSizeOf(impl.getFrameInfoNames()))
                        .add(NonmovableArrays.byteSizeOf(impl.getDeoptimizationStartOffsets()))
                        .add(NonmovableArrays.byteSizeOf(impl.getDeoptimizationEncodings()))
                        .add(NonmovableArrays.byteSizeOf(impl.getDeoptimizationObjectConstants()))
                        .add(NonmovableArrays.byteSizeOf(impl.getObjectsReferenceMapEncoding()));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean contains(CodeInfo info, CodePointer ip) {
        CodeInfoImpl impl = cast(info);
        return ((UnsignedWord) ip).subtract((UnsignedWord) impl.getCodeStart()).belowThan(impl.getCodeSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long relativeIP(CodeInfo info, CodePointer ip) {
        assert contains(info, ip);
        return ((UnsignedWord) ip).subtract((UnsignedWord) cast(info).getCodeStart()).rawValue();
    }

    public static CodePointer absoluteIP(CodeInfo info, long relativeIP) {
        return (CodePointer) ((UnsignedWord) cast(info).getCodeStart()).add(WordFactory.unsigned(relativeIP));
    }

    public static long initFrameInfoReader(CodeInfo info, CodePointer ip, ReusableTypeReader frameInfoReader) {
        long entryOffset = CodeInfoDecoder.lookupCodeInfoEntryOffset(info, relativeIP(info, ip));
        if (entryOffset >= 0) {
            if (!CodeInfoDecoder.initFrameInfoReader(info, entryOffset, frameInfoReader)) {
                return -1;
            }
        }
        return entryOffset;
    }

    public static FrameInfoQueryResult nextFrameInfo(CodeInfo info, long entryOffset, ReusableTypeReader frameInfoReader,
                    FrameInfoDecoder.FrameInfoQueryResultAllocator resultAllocator, ValueInfoAllocator valueInfoAllocator, boolean fetchFirstFrame) {
        int entryFlags = CodeInfoDecoder.loadEntryFlags(info, entryOffset);
        boolean isDeoptEntry = CodeInfoDecoder.extractFI(entryFlags) == CodeInfoDecoder.FI_DEOPT_ENTRY_INDEX_S4;
        return FrameInfoDecoder.decodeFrameInfo(isDeoptEntry, frameInfoReader, info, resultAllocator, valueInfoAllocator, fetchFirstFrame);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> T getObjectField(CodeInfo info, int index) {
        return (T) NonmovableArrays.getObject(cast(info).getObjectFields(), index);
    }

    public static String getName(CodeInfo info) {
        return getObjectField(info, CodeInfoImpl.NAME_OBJFIELD);
    }

    public static long lookupDeoptimizationEntrypoint(CodeInfo info, long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(info, method, encodedBci, codeInfo);
    }

    public static long lookupTotalFrameSize(CodeInfo info, long ip) {
        SimpleCodeInfoQueryResult codeInfoQueryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
        lookupCodeInfo(info, ip, codeInfoQueryResult);
        return CodeInfoQueryResult.getTotalFrameSize(codeInfoQueryResult.getEncodedFrameSize());
    }

    public static NonmovableArray<Byte> getReferenceMapEncoding(CodeInfo info) {
        return cast(info).getReferenceMapEncoding();
    }

    public static long lookupReferenceMapIndex(CodeInfo info, long ip) {
        return CodeInfoDecoder.lookupReferenceMapIndex(info, ip);
    }

    public static void lookupCodeInfo(CodeInfo info, long ip, CodeInfoQueryResult codeInfoQueryResult) {
        CodeInfoDecoder.lookupCodeInfo(info, ip, codeInfoQueryResult);
    }

    public static void lookupCodeInfo(CodeInfo info, long ip, SimpleCodeInfoQueryResult codeInfoQueryResult) {
        CodeInfoDecoder.lookupCodeInfo(info, ip, codeInfoQueryResult);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    public static void setFrameInfo(CodeInfo info, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants,
                    NonmovableObjectArray<Class<?>> sourceClasses, NonmovableObjectArray<String> sourceMethodNames, NonmovableObjectArray<String> names) {
        CodeInfoImpl impl = cast(info);
        impl.setFrameInfoEncodings(encodings);
        impl.setFrameInfoObjectConstants(objectConstants);
        impl.setFrameInfoSourceClasses(sourceClasses);
        impl.setFrameInfoSourceMethodNames(sourceMethodNames);
        impl.setFrameInfoNames(names);
    }

    public static void setCodeInfo(CodeInfo info, NonmovableArray<Byte> index, NonmovableArray<Byte> encodings, NonmovableArray<Byte> referenceMapEncoding) {
        CodeInfoImpl impl = cast(info);
        impl.setCodeInfoIndex(index);
        impl.setCodeInfoEncodings(encodings);
        impl.setReferenceMapEncoding(referenceMapEncoding);
    }

    public static Log log(CodeInfo info, Log log) {
        return info.isNull() ? log.string("null") : log.string(CodeInfo.class.getName()).string("@").hex(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getTier(CodeInfo info) {
        return cast(info).getTier();
    }

    static NonmovableArray<Integer> getDeoptimizationStartOffsets(CodeInfo info) {
        return cast(info).getDeoptimizationStartOffsets();
    }

    static NonmovableArray<Byte> getDeoptimizationEncodings(CodeInfo info) {
        return cast(info).getDeoptimizationEncodings();
    }

    static NonmovableObjectArray<Object> getDeoptimizationObjectConstants(CodeInfo info) {
        return cast(info).getDeoptimizationObjectConstants();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static CodePointer getCodeEnd(CodeInfo info) {
        CodeInfoImpl impl = cast(info);
        return (CodePointer) ((UnsignedWord) impl.getCodeStart()).add(impl.getCodeSize());
    }

    public static NonmovableArray<Byte> getCodeInfoIndex(CodeInfo info) {
        return cast(info).getCodeInfoIndex();
    }

    public static NonmovableArray<Byte> getCodeInfoEncodings(CodeInfo info) {
        return cast(info).getCodeInfoEncodings();
    }

    public static NonmovableArray<Byte> getFrameInfoEncodings(CodeInfo info) {
        return cast(info).getFrameInfoEncodings();
    }

    public static NonmovableObjectArray<Object> getFrameInfoObjectConstants(CodeInfo info) {
        return cast(info).getFrameInfoObjectConstants();
    }

    public static NonmovableObjectArray<Class<?>> getFrameInfoSourceClasses(CodeInfo info) {
        return cast(info).getFrameInfoSourceClasses();
    }

    public static NonmovableObjectArray<String> getFrameInfoSourceMethodNames(CodeInfo info) {
        return cast(info).getFrameInfoSourceMethodNames();
    }

    public static NonmovableObjectArray<String> getFrameInfoNames(CodeInfo info) {
        return cast(info).getFrameInfoNames();
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    private static CodeInfoImpl cast(UntetheredCodeInfo info) {
        assert isValid(info);
        return (CodeInfoImpl) info;
    }
}
