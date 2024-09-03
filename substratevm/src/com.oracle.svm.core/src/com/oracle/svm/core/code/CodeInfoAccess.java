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
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.FrameInfoDecoder.ConstantAccess;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Provides functionality to query information about a unit of compiled code from a {@link CodeInfo}
 * object. This helper class is necessary to ensure that {@link CodeInfo} objects are used
 * correctly, as they are garbage collected even though they live in unmanaged memory. For that
 * purpose, every {@link CodeInfo} object has a tether object. The garbage collector can free a
 * {@link CodeInfo} object if its tether object is unreachable at a safepoint, that is, in
 * <b>ANY</b> method that is <b>NOT</b> annotated with {@link Uninterruptible}. Even a blocking VM
 * operation that needs a safepoint won't guarantee that the {@link CodeInfo} object is kept alive
 * because GCs can be triggered within VM operations as well.
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
        return RuntimeAssertionsSupport.singleton().desiredAssertionStatus(CodeInfoAccess.class);
    }

    @Uninterruptible(reason = "The handle should only be accessed from uninterruptible code to prevent that the GC frees the CodeInfo.", callerMustBe = true)
    public static Object acquireTether(UntetheredCodeInfo info) {
        Object tether = UntetheredCodeInfoAccess.getTetherUnsafe(info);
        /*
         * Do not interact with the tether object during GCs, as the reference might be forwarded
         * and therefore not safe to access. Tethering is not needed then, either.
         */
        assert UntetheredCodeInfoAccess.isAOTImageCode(info) || VMOperation.isGCInProgress() || ((CodeInfoTether) tether).incrementCount() > 0;
        return tether;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    @NeverInline("Prevent elimination of object reference in caller.")
    public static void releaseTether(UntetheredCodeInfo info, Object tether) {
        assert VMOperation.isGCInProgress() || UntetheredCodeInfoAccess.getTetherUnsafe(info) == null || UntetheredCodeInfoAccess.getTetherUnsafe(info) == tether;
        assert UntetheredCodeInfoAccess.isAOTImageCode(info) || VMOperation.isGCInProgress() || ((CodeInfoTether) tether).decrementCount() >= 0;
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
        return unsafeConvert(untetheredInfo);
    }

    /**
     * Try to avoid using this method. It is similar to {@link #convert(UntetheredCodeInfo, Object)}
     * but with less verification.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo unsafeConvert(UntetheredCodeInfo untetheredInfo) {
        assert isValid(untetheredInfo);
        return (CodeInfo) untetheredInfo;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isValid(UntetheredCodeInfo info) {
        return SubstrateUtil.HOSTED || !haveAssertions() || VMOperation.isGCInProgress() || UntetheredCodeInfoAccess.getTetherUnsafe(info) == null ||
                        ((CodeInfoTether) UntetheredCodeInfoAccess.getTetherUnsafe(info)).getCount() > 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setState(CodeInfo info, int state) {
        assert getState(info) < state;
        cast(info).setState(state);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getState(CodeInfo info) {
        return cast(info).getState();
    }

    public static String stateToString(int codeInfoState) {
        switch (codeInfoState) {
            case CodeInfo.STATE_CREATED:
                return "created";
            case CodeInfo.STATE_CODE_CONSTANTS_LIVE:
                return "code constants live";
            case CodeInfo.STATE_NON_ENTRANT:
                return "non-entrant";
            case CodeInfo.STATE_READY_FOR_INVALIDATION:
                return "ready for invalidation";
            case CodeInfo.STATE_INVALIDATED:
                return "invalidated";
            case CodeInfo.STATE_UNREACHABLE:
                return "unreachable";
            case CodeInfo.STATE_FREED:
                return "invalid (freed)";
            default:
                return "invalid state";
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAlive(CodeInfo info) {
        return isAliveState(cast(info).getState());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAliveState(int state) {
        return state == CodeInfo.STATE_CODE_CONSTANTS_LIVE || state == CodeInfo.STATE_NON_ENTRANT;
    }

    /** @see CodeInfoImpl#getCodeStart */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodePointer getCodeStart(CodeInfo info) {
        return cast(info).getCodeStart();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getCodeEntryPointOffset(CodeInfo info) {
        return cast(info).getCodeEntryPointOffset();
    }

    /** @see CodeInfoImpl#getCodeSize */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getCodeSize(CodeInfo info) {
        return cast(info).getCodeSize();
    }

    /** @see CodeInfoImpl#getDataSize */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getDataSize(CodeInfo info) {
        return cast(info).getDataSize();
    }

    /** @see CodeInfoImpl#getDataOffset */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getDataOffset(CodeInfo info) {
        return cast(info).getDataOffset();
    }

    /** @see CodeInfoImpl#getCodeAndDataMemorySize */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getCodeAndDataMemorySize(CodeInfo info) {
        return cast(info).getCodeAndDataMemorySize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getNativeMetadataSize(CodeInfo info) {
        CodeInfoImpl impl = cast(info);
        UnsignedWord size = SizeOf.unsigned(CodeInfo.class);
        if (!impl.getAllObjectsAreInImageHeap()) {
            size = size.add(NonmovableArrays.byteSizeOf(impl.getObjectFields()))
                            .add(NonmovableArrays.byteSizeOf(impl.getCodeInfoIndex()))
                            .add(NonmovableArrays.byteSizeOf(impl.getCodeInfoEncodings()))
                            .add(NonmovableArrays.byteSizeOf(impl.getStackReferenceMapEncoding()))
                            .add(NonmovableArrays.byteSizeOf(impl.getFrameInfoEncodings()))
                            .add(NonmovableArrays.byteSizeOf(impl.getObjectConstants()))
                            .add(NonmovableArrays.byteSizeOf(impl.getClasses()))
                            .add(NonmovableArrays.byteSizeOf(impl.getMemberNames()))
                            .add(NonmovableArrays.byteSizeOf(impl.getOtherStrings()))
                            .add(NonmovableArrays.byteSizeOf(impl.getMethodTable()))
                            .add(NonmovableArrays.byteSizeOf(impl.getDeoptimizationStartOffsets()))
                            .add(NonmovableArrays.byteSizeOf(impl.getDeoptimizationEncodings()))
                            .add(NonmovableArrays.byteSizeOf(impl.getDeoptimizationObjectConstants()))
                            .add(NonmovableArrays.byteSizeOf(impl.getCodeConstantsReferenceMapEncoding()));
        }
        return size;
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

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static <T> T getObjectField(CodeInfo info, int index) {
        return (T) NonmovableArrays.getObject(cast(info).getObjectFields(), index);
    }

    public static String getName(CodeInfo info) {
        return getObjectField(info, CodeInfoImpl.NAME_OBJFIELD);
    }

    public static long lookupDeoptimizationEntrypoint(CodeInfo info, long method, long encodedBci, CodeInfoQueryResult codeInfo, ConstantAccess constantAccess) {
        return CodeInfoDecoder.lookupDeoptimizationEntrypoint(info, method, encodedBci, codeInfo, constantAccess);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long lookupTotalFrameSize(CodeInfo info, long relativeIP) {
        return CodeInfoQueryResult.getTotalFrameSize(lookupEncodedFrameSize(info, relativeIP));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long lookupTotalFrameSize(CodeInfo info, CodePointer ip) {
        return CodeInfoQueryResult.getTotalFrameSize(lookupEncodedFrameSize(info, ip));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> getStackReferenceMapEncoding(CodeInfo info) {
        return cast(info).getStackReferenceMapEncoding();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long lookupStackReferenceMapIndex(CodeInfo info, long relativeIP) {
        return CodeInfoDecoder.lookupStackReferenceMapIndex(info, relativeIP);
    }

    public static void lookupCodeInfo(CodeInfo info, CodePointer ip, CodeInfoQueryResult codeInfoQueryResult) {
        lookupCodeInfo(info, ip, codeInfoQueryResult, FrameInfoDecoder.SubstrateConstantAccess);
    }

    public static void lookupCodeInfo(CodeInfo info, CodePointer ip, CodeInfoQueryResult codeInfoQueryResult, ConstantAccess constantAccess) {
        long relativeIP = CodeInfoAccess.relativeIP(info, ip);
        CodeInfoDecoder.lookupCodeInfo(info, relativeIP, codeInfoQueryResult, constantAccess);
    }

    public static void lookupCodeInfo(CodeInfo info, long relativeIP, CodeInfoQueryResult codeInfoQueryResult, ConstantAccess constantAccess) {
        CodeInfoDecoder.lookupCodeInfo(info, relativeIP, codeInfoQueryResult, constantAccess);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void lookupCodeInfo(CodeInfo info, CodePointer ip, SimpleCodeInfoQueryResult codeInfoQueryResult) {
        long relativeIP = CodeInfoAccess.relativeIP(info, ip);
        CodeInfoDecoder.lookupCodeInfo(info, relativeIP, codeInfoQueryResult);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long lookupEncodedFrameSize(CodeInfo info, CodePointer ip) {
        long relativeIP = CodeInfoAccess.relativeIP(info, ip);
        return lookupEncodedFrameSize(info, relativeIP);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long lookupEncodedFrameSize(CodeInfo info, long relativeIP) {
        SimpleCodeInfoQueryResult codeInfoQueryResult = UnsafeStackValue.get(SimpleCodeInfoQueryResult.class);
        CodeInfoDecoder.lookupCodeInfo(info, relativeIP, codeInfoQueryResult);
        return codeInfoQueryResult.getEncodedFrameSize();
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    public static void setFrameInfo(CodeInfo info, NonmovableArray<Byte> encodings) {
        CodeInfoImpl impl = cast(info);
        impl.setFrameInfoEncodings(encodings);
    }

    public static void setCodeInfo(CodeInfo info, NonmovableArray<Byte> index, NonmovableArray<Byte> encodings, NonmovableArray<Byte> referenceMapEncoding) {
        CodeInfoImpl impl = cast(info);
        impl.setCodeInfoIndex(index);
        impl.setCodeInfoEncodings(encodings);
        impl.setStackReferenceMapEncoding(referenceMapEncoding);
    }

    @Uninterruptible(reason = "Nonmovable object arrays are not visible to GC until installed.")
    public static void setEncodings(CodeInfo info, NonmovableObjectArray<Object> objectConstants, NonmovableObjectArray<Class<?>> classes,
                    NonmovableObjectArray<String> memberNames, NonmovableObjectArray<String> otherStrings, NonmovableArray<Byte> methodTable, int methodTableFirstId) {
        CodeInfoImpl impl = cast(info);
        impl.setObjectConstants(objectConstants);
        impl.setClasses(classes);
        impl.setMemberNames(memberNames);
        impl.setOtherStrings(otherStrings);
        impl.setMethodTable(methodTable);
        impl.setMethodTableFirstId(methodTableFirstId);
        if (!SubstrateUtil.HOSTED) {
            // notify the GC about the frame metadata that is now live
            Heap.getHeap().getRuntimeCodeInfoGCSupport().registerFrameMetadata(impl);
        }
    }

    public static Log log(CodeInfo info, Log log) {
        return info.isNull() ? log.string("null") : log.string("CodeInfo@").hex(info);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> getCodeInfoIndex(CodeInfo info) {
        return cast(info).getCodeInfoIndex();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> getCodeInfoEncodings(CodeInfo info) {
        return cast(info).getCodeInfoEncodings();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> getFrameInfoEncodings(CodeInfo info) {
        return cast(info).getFrameInfoEncodings();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableObjectArray<Object> getObjectConstants(CodeInfo info) {
        return cast(info).getObjectConstants();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableObjectArray<Class<?>> getClasses(CodeInfo info) {
        return cast(info).getClasses();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableObjectArray<String> getMemberNames(CodeInfo info) {
        return cast(info).getMemberNames();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableObjectArray<String> getOtherStrings(CodeInfo info) {
        return cast(info).getOtherStrings();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> getMethodTable(CodeInfo info) {
        return cast(info).getMethodTable();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int getMethodTableFirstId(CodeInfo info) {
        return cast(info).getMethodTableFirstId();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isAOTImageCode(CodeInfo info) {
        return cast(info).getIsAOTImageCode();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static CodeInfo getNextImageCodeInfo(CodeInfo info) {
        assert isAOTImageCode(info);
        return cast(info).getNextImageCodeInfo();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CodeInfoImpl cast(UntetheredCodeInfo info) {
        assert isValid(info);
        return (CodeInfoImpl) info;
    }

    public static void printCodeInfo(Log log, CodeInfo info, boolean allowJavaHeapAccess) {
        String name = null;
        HasInstalledCode hasInstalledCode = HasInstalledCode.Unknown;
        SubstrateInstalledCode installedCode = null;
        if (allowJavaHeapAccess) {
            name = CodeInfoAccess.getName(info);
            installedCode = RuntimeCodeInfoAccess.getInstalledCode(info);
            hasInstalledCode = (installedCode == null) ? HasInstalledCode.No : HasInstalledCode.Yes;
        }

        printCodeInfo(log, info, CodeInfoAccess.getState(info), name, CodeInfoAccess.getCodeStart(info), CodeInfoAccess.getCodeEnd(info), hasInstalledCode, installedCode);
    }

    public static void printCodeInfo(Log log, UntetheredCodeInfo info, int state, String name, CodePointer codeStart, CodePointer codeEnd, HasInstalledCode hasInstalledCode,
                    SubstrateInstalledCode installedCode) {
        long installedCodeAddress = 0;
        long installedCodeEntryPoint = 0;
        if (installedCode != null) {
            assert hasInstalledCode == HasInstalledCode.Yes : hasInstalledCode;
            installedCodeAddress = installedCode.getAddress();
            installedCodeEntryPoint = installedCode.getEntryPoint();
        }

        printCodeInfo(log, info, state, name, codeStart, codeEnd, hasInstalledCode, installedCodeAddress, installedCodeEntryPoint);
    }

    public static void printCodeInfo(Log log, UntetheredCodeInfo codeInfo, int state, String name, CodePointer codeStart, CodePointer codeEnd, HasInstalledCode hasInstalledCode,
                    long installedCodeAddress, long installedCodeEntryPoint) {
        log.string("CodeInfo (").zhex(codeInfo).string(" - ").zhex(((UnsignedWord) codeInfo).add(CodeInfoAccess.getSizeOfCodeInfo()).subtract(1)).string("), ")
                        .string(CodeInfoAccess.stateToString(state));
        if (name != null) {
            log.string(" - ").string(name);
        }
        log.string(", ip: (").zhex(codeStart).string(" - ").zhex(codeEnd).string(")");

        switch (hasInstalledCode) {
            case Yes:
                log.string(", installedCode: (address: ").zhex(installedCodeAddress).string(", entryPoint: ").zhex(installedCodeEntryPoint).string(")");
                break;
            case No:
                log.string(", installedCode: null.");
                break;
            case Unknown:
                // nothing to do.
                break;
            default:
                throw VMError.shouldNotReachHere("Unexpected value for HasInstalledCode");
        }
    }

    @Fold
    public static UnsignedWord getSizeOfCodeInfo() {
        return SizeOf.unsigned(CodeInfoImpl.class);
    }

    public enum HasInstalledCode {
        Yes,
        No,
        Unknown
    }
}
