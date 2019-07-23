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
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;

/**
 * Functionality to query {@link CodeInfo} for information about a unit of compiled code.
 * <p>
 * Callers of these methods must take into account that frames on the stack can be deoptimized at
 * any safepoint check, that is, in any method that is not annotated with {@link Uninterruptible}
 * unless it is executed in a {@linkplain VMOperation VM operation in a safepoint}. When a method is
 * deoptimized, its code can also be {@linkplain CodeInfoTable#invalidateInstalledCode invalidated},
 * successive {@linkplain CodeInfoTable#lookupCodeInfo lookups of instruction pointers} within the
 * deoptimized code fail, and any existing pointers to the {@link CodeInfo} become invalid and
 * accesses with them lead to a crash.
 * <p>
 * This can be avoided by {@linkplain Uninterruptible uninterruptibly} reading the current
 * instruction pointer from the stack, calling {@link #lookupCodeInfo} to obtain the corresponding
 * {@link CodeInfo}, and then {@linkplain #acquireTether acquiring a "tether" object} and keeping
 * that object referenced through a variable to ensure that the {@link CodeInfo} and its data remain
 * accessible. At that point, processing can continue in interruptible code (calling a separate
 * method with {@link Uninterruptible#calleeMustBe()} == false is recommended). Note however that
 * the frame on the stack can nevertheless be deoptimized at a safepoint check, and later lookups
 * via the instruction pointer can fail, so only the acquired {@link CodeInfo} must be used.
 * Eventually, the tether must be {@linkplain #releaseTether(CodeInfo, Object) released}, and its
 * reference must be set to null. (See usages of these methods for concrete code examples.)
 */
public final class CodeInfoAccess {
    private CodeInfoAccess() {
    }

    @Fold
    static boolean haveAssertions() {
        return SubstrateOptions.getRuntimeAssertionsForClass(CodeInfoAccess.class.getName());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Object acquireTether(CodeInfo info) {
        Object tether = getObjectField(info, CodeInfo.TETHER_OBJFIELD);
        /*
         * Do not interact with the tether object during VM ops, it could be during GC while the
         * reference is not safe to access (e.g. forwarded). Tethering is not needed then, either.
         */
        assert VMOperation.isInProgress() || ((UninterruptibleUtils.AtomicInteger) tether).incrementAndGet() > 0;
        return tether;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isTethered(CodeInfo info) {
        return !haveAssertions() || VMOperation.isInProgress() || ((UninterruptibleUtils.AtomicInteger) getObjectField(info, CodeInfo.TETHER_OBJFIELD)).get() > 0;
    }

    @NeverInline("Prevent elimination of object reference in caller.")
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void releaseTether(CodeInfo info, Object tether) {
        assert tether == getObjectField(info, CodeInfo.TETHER_OBJFIELD) || getObjectField(info, CodeInfo.TETHER_OBJFIELD) == null;
        assert VMOperation.isInProgress() || ((UninterruptibleUtils.AtomicInteger) tether).getAndDecrement() > 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodePointer getCodeStart(CodeInfo info) {
        return info.getCodeStart();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getCodeSize(CodeInfo info) {
        return info.getCodeSize();
    }

    public static UnsignedWord getMetadataSize(CodeInfo info) {
        return SizeOf.unsigned(CodeInfo.class)
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean contains(CodeInfo info, CodePointer ip) {
        return ((UnsignedWord) ip).subtract((UnsignedWord) info.getCodeStart()).belowThan(info.getCodeSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long relativeIP(CodeInfo info, CodePointer ip) {
        assert contains(info, ip);
        return ((UnsignedWord) ip).subtract((UnsignedWord) info.getCodeStart()).rawValue();
    }

    public static CodePointer absoluteIP(CodeInfo info, long relativeIP) {
        return (CodePointer) ((UnsignedWord) info.getCodeStart()).add(WordFactory.unsigned(relativeIP));
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
        return (T) NonmovableArrays.getObject(info.getObjectFields(), index);
    }

    public static String getName(CodeInfo info) {
        return getObjectField(info, CodeInfo.NAME_OBJFIELD);
    }

    public static long lookupDeoptimizationEntrypoint(CodeInfo info, long method, long encodedBci, CodeInfoQueryResult codeInfo) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(info, method, encodedBci, codeInfo);
    }

    public static long lookupTotalFrameSize(CodeInfo info, long ip) {
        return CodeInfoDecoder.lookupTotalFrameSize(info, ip);
    }

    public static long lookupExceptionOffset(CodeInfo info, long ip) {
        return CodeInfoDecoder.lookupExceptionOffset(info, ip);
    }

    public static NonmovableArray<Byte> getReferenceMapEncoding(CodeInfo info) {
        return info.getReferenceMapEncoding();
    }

    public static long lookupReferenceMapIndex(CodeInfo info, long ip) {
        return CodeInfoDecoder.lookupReferenceMapIndex(info, ip);
    }

    public static void lookupCodeInfo(CodeInfo info, long ip, CodeInfoQueryResult codeInfo) {
        CodeInfoDecoder.lookupCodeInfo(info, ip, codeInfo);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    public static void setFrameInfo(CodeInfo info, NonmovableArray<Byte> encodings, NonmovableObjectArray<Object> objectConstants,
                    NonmovableObjectArray<Class<?>> sourceClasses, NonmovableObjectArray<String> sourceMethodNames, NonmovableObjectArray<String> names) {

        info.setFrameInfoEncodings(encodings);
        info.setFrameInfoObjectConstants(objectConstants);
        info.setFrameInfoSourceClasses(sourceClasses);
        info.setFrameInfoSourceMethodNames(sourceMethodNames);
        info.setFrameInfoNames(names);
    }

    public static void setCodeInfo(CodeInfo info, NonmovableArray<Byte> index, NonmovableArray<Byte> encodings, NonmovableArray<Byte> referenceMapEncoding) {
        info.setCodeInfoIndex(index);
        info.setCodeInfoEncodings(encodings);
        info.setReferenceMapEncoding(referenceMapEncoding);
    }

    public static Log log(CodeInfo info, Log log) {
        return info.isNull() ? log.string("null") : log.string(CodeInfo.class.getName()).string("@").hex(info);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getTier(CodeInfo info) {
        return info.getTier();
    }

    static NonmovableArray<Integer> getDeoptimizationStartOffsets(CodeInfo info) {
        return info.getDeoptimizationStartOffsets();
    }

    static NonmovableArray<Byte> getDeoptimizationEncodings(CodeInfo info) {
        return info.getDeoptimizationEncodings();
    }

    static NonmovableObjectArray<Object> getDeoptimizationObjectConstants(CodeInfo info) {
        return info.getDeoptimizationObjectConstants();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static CodePointer getCodeEnd(CodeInfo info) {
        return (CodePointer) ((UnsignedWord) info.getCodeStart()).add(info.getCodeSize());
    }
}
