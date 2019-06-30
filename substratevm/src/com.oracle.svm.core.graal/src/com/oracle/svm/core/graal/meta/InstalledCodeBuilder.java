/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.DeoptimizationSourcePositionEncoder;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.RuntimeMethodInfoAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.NativeImagePatcher;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.heap.CodeReferenceMapEncoder;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaKind;

/**
 * Represents the installed code of a runtime compiled method.
 * <p>
 * It allocates executable memory for the machine code + the constants. In addition it allocates
 * data memory (not on the regular heap!) for the needed system objects: code chunk infos, encoded
 * pointer map for the constants.
 * <p>
 * The SubstrateInstalledCode also acts as an allocator to allocate all these system objects.
 *
 */
public class InstalledCodeBuilder {
    private final SharedRuntimeMethod method;
    private final SubstrateInstalledCode installedCode;
    private final int tier;
    private final Map<SharedMethod, InstalledCodeBuilder> allInstalledCode;
    protected Pointer code;
    private final int codeSize;
    private final int constantsOffset;
    private final InstalledCodeObserver[] codeObservers;

    private SubstrateCompilationResult compilation;
    private byte[] compiledBytes;

    private CodeInfo runtimeMethodInfo;

    private final boolean testTrampolineJumps;

    /**
     * The size for trampoline jumps: jmp [rip+offset]
     * <p>
     * Trampoline jumps are added immediately after the method code, where each jump needs 6 bytes.
     * The jump instructions reference the 8-byte destination addresses, which are allocated after
     * the jumps.
     */
    static final int TRAMPOLINE_JUMP_SIZE = 6;

    public InstalledCodeBuilder(SharedRuntimeMethod method, CompilationResult compilation, SubstrateInstalledCode installedCode, Map<SharedMethod, InstalledCodeBuilder> allInstalledCode) {
        this(method, compilation, installedCode, allInstalledCode, false);
    }

    @SuppressWarnings("try")
    public InstalledCodeBuilder(SharedRuntimeMethod method, CompilationResult compilation, SubstrateInstalledCode installedCode, Map<SharedMethod, InstalledCodeBuilder> allInstalledCode,
                    boolean testTrampolineJumps) {
        this.method = method;
        this.compilation = (SubstrateCompilationResult) compilation;
        this.tier = compilation.getName().endsWith(TruffleCompiler.FIRST_TIER_COMPILATION_SUFFIX) ? TruffleCompiler.FIRST_TIER_INDEX : TruffleCompiler.LAST_TIER_INDEX;
        this.installedCode = installedCode;
        this.allInstalledCode = allInstalledCode;
        this.testTrampolineJumps = testTrampolineJumps;

        DebugContext debug = DebugContext.forCurrentThread();
        try (Indent indent = debug.logAndIndent("create installed code of %s.%s", method.getDeclaringClass().getName(), method.getName())) {
            TargetDescription target = ConfigurationValues.getTarget();

            if (target.arch.getPlatformKind(JavaKind.Object).getSizeInBytes() != 8) {
                throw VMError.shouldNotReachHere("wrong object size");
            }

            int constantsSize = compilation.getDataSection().getSectionSize();
            codeSize = compilation.getTargetCodeSize();
            int tmpConstantsOffset = NumUtil.roundUp(codeSize, compilation.getDataSection().getSectionAlignment());
            int tmpMemorySize = tmpConstantsOffset + constantsSize;

            // Allocate executable memory. It contains the compiled code and the constants
            code = allocateOSMemory(WordFactory.unsigned(tmpMemorySize));

            /*
             * Check if we there are some direct calls where the PC displacement is out of the 32
             * bit range. It should be a rare case, but we need to handle it. In such a case we
             * insert trampoline jumps after the code.
             *
             * This could be even improved by using "call [rip+offset]" instructions. But it's not
             * trivial because these instructions need one byte more than the original PC relative
             * calls.
             */
            Set<Long> directTargets = new HashSet<>();
            boolean needTrampolineJumps = testTrampolineJumps;
            for (Infopoint infopoint : compilation.getInfopoints()) {
                if (infopoint instanceof Call && ((Call) infopoint).direct) {
                    Call call = (Call) infopoint;
                    long targetAddress = getTargetCodeAddress(call);
                    long pcDisplacement = targetAddress - (code.rawValue() + call.pcOffset);
                    if (pcDisplacement != (int) pcDisplacement) {
                        needTrampolineJumps = true;
                    }
                    directTargets.add(targetAddress);
                }
            }
            compiledBytes = compilation.getTargetCode();
            if (needTrampolineJumps) {
                /*
                 * Reserve some space after the code to insert the trampoline jumps + the target
                 * addresses. We reserve space for _all_ calls (worst case), because we need to
                 * re-allocate the memory (it got larger). So we don't know which calls will need
                 * trampoline jumps with the new code address.
                 */
                freeOSMemory(code, WordFactory.unsigned(tmpMemorySize));

                // Add space for the actual trampoline jump instructions: jmp [rip+offset]
                tmpConstantsOffset = NumUtil.roundUp(codeSize + directTargets.size() * TRAMPOLINE_JUMP_SIZE, 8);
                // Add space for the target addresses
                // (which are referenced from the jump instructions)
                tmpConstantsOffset = NumUtil.roundUp(tmpConstantsOffset + directTargets.size() * 8, compilation.getDataSection().getSectionAlignment());
                if (tmpConstantsOffset > compiledBytes.length) {
                    compiledBytes = Arrays.copyOf(compiledBytes, tmpConstantsOffset);
                }
                tmpMemorySize = tmpConstantsOffset + constantsSize;

                code = allocateOSMemory(WordFactory.unsigned(tmpMemorySize));
            }
            constantsOffset = tmpConstantsOffset;

            codeObservers = ImageSingletons.lookup(InstalledCodeObserverSupport.class).createObservers(debug, method, compilation, code);
        }

    }

    public SubstrateInstalledCode getInstalledCode() {
        return installedCode;
    }

    static class ObjectConstantsHolder {
        final SubstrateReferenceMap referenceMap;
        final int[] offsets;
        final NativeImagePatcher[] patchers;
        final Object[] values;
        int count;

        ObjectConstantsHolder(CompilationResult compilation) {
            /* Conservative estimate on the maximum number of object constants we might have. */
            int maxDataRefs = compilation.getDataSection().getSectionSize() / ConfigurationValues.getObjectLayout().getReferenceSize();
            int maxCodeRefs = compilation.getDataPatches().size();
            offsets = new int[maxDataRefs + maxCodeRefs];
            patchers = new NativeImagePatcher[offsets.length];
            values = new Object[offsets.length];
            referenceMap = new SubstrateReferenceMap();
        }

        void add(NativeImagePatcher patchingAnnotation, SubstrateObjectConstant constant) {
            assert constant.isCompressed() == ReferenceAccess.singleton().haveCompressedReferences() : "Object reference constants in code must be compressed";
            patchers[count] = patchingAnnotation;
            values[count] = KnownIntrinsics.convertUnknownValue(constant.getObject(), Object.class);
            referenceMap.markReferenceAtOffset(patchingAnnotation.getPosition(), true);
            count++;
        }
    }

    public void install() {
        this.installOperation();
    }

    /**
     * The layout of the data-section is determined by Substratevm and not by the underlying
     * architecture. We can use the same patcher for all architectures.
     */
    private static class DataSectionPatcher implements NativeImagePatcher {
        private final int position;

        DataSectionPatcher(int position) {
            this.position = position;
        }

        @Override
        public void patch(int codePos, int relative, byte[] code) {
            shouldNotReachHere("Datasection can only be patched with an VM constant");
        }

        @Uninterruptible(reason = "The patcher is intended to work with raw pointers")
        @Override
        public void patchData(Pointer pointer, Object object) {
            boolean compressed = ReferenceAccess.singleton().haveCompressedReferences();
            Pointer address = pointer.add(position);
            ReferenceAccess.singleton().writeObjectAt(address, object, compressed);
        }

        @Uninterruptible(reason = ".")
        @Override
        public int getPosition() {
            return position;
        }
    }

    @SuppressWarnings("try")
    private void installOperation() {
        /*
         * Object reference constants are stored in this holder first, then written and made visible
         * in a single step that is atomic regarding to GC.
         */
        ObjectConstantsHolder objectConstants = new ObjectConstantsHolder(compilation);

        // Build an index of PatchingAnnoations
        Map<Integer, NativeImagePatcher> patches = new HashMap<>();
        for (CodeAnnotation codeAnnotation : compilation.getCodeAnnotations()) {
            if (codeAnnotation instanceof NativeImagePatcher) {
                patches.put(codeAnnotation.position, (NativeImagePatcher) codeAnnotation);
            }
        }
        patchData(patches, objectConstants);

        int updatedCodeSize = patchCalls(patches);
        assert updatedCodeSize <= constantsOffset;

        // Store the compiled code
        for (int index = 0; index < updatedCodeSize; index++) {
            code.writeByte(index, compiledBytes[index]);
        }

        /* Write primitive constants to the buffer, record object constants with offsets */
        ByteBuffer constantsBuffer = CTypeConversion.asByteBuffer(code.add(constantsOffset), compilation.getDataSection().getSectionSize());
        compilation.getDataSection().buildDataSection(constantsBuffer, (position, constant) -> {
            objectConstants.add(new DataSectionPatcher(constantsOffset + position), (SubstrateObjectConstant) constant);
        });

        runtimeMethodInfo = RuntimeMethodInfoAccess.allocateMethodInfo();
        RuntimeMethodInfoAccess.setCodeLocation(runtimeMethodInfo, code, codeSize);

        CodeReferenceMapEncoder encoder = new CodeReferenceMapEncoder();
        encoder.add(objectConstants.referenceMap);
        RuntimeMethodInfoAccess.setCodeObjectConstantsInfo(runtimeMethodInfo, encoder.encodeAll(), encoder.lookupEncoding(objectConstants.referenceMap));
        writeObjectConstantsToCode(objectConstants);

        createCodeChunkInfos();

        InstalledCodeObserver.InstalledCodeObserverHandle[] observerHandles = InstalledCodeObserverSupport.installObservers(codeObservers);

        RuntimeMethodInfoAccess.setData(runtimeMethodInfo, installedCode, tier, observerHandles);

        Throwable[] errorBox = {null};
        VMOperation.enqueueBlockingSafepoint("Install code", () -> {
            try {
                CodeInfoTable.getRuntimeCodeCache().addMethod(runtimeMethodInfo);
                /*
                 * This call makes the new code visible, i.e., other threads can start executing it
                 * immediately. So all metadata must be registered at this point.
                 */
                installedCode.setAddress(code.rawValue(), method);
            } catch (Throwable e) {
                errorBox[0] = e;
            }
        });
        if (errorBox[0] != null) {
            throw rethrow(errorBox[0]);
        }

        compilation = null;
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    @Uninterruptible(reason = "Must be atomic with regard to garbage collection.")
    private void writeObjectConstantsToCode(ObjectConstantsHolder objectConstants) {
        for (int i = 0; i < objectConstants.count; i++) {
            objectConstants.patchers[i].patchData(code, objectConstants.values[i]);
        }
        RuntimeMethodInfoAccess.setCodeConstantsLive(runtimeMethodInfo);
    }

    private void createCodeChunkInfos() {
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(new FrameInfoEncoder.NamesFromImage());
        codeInfoEncoder.addMethod(method, compilation, 0);
        codeInfoEncoder.encodeAllAndInstall(runtimeMethodInfo);
        assert CodeInfoEncoder.verifyMethod(compilation, 0, runtimeMethodInfo);

        DeoptimizationSourcePositionEncoder sourcePositionEncoder = new DeoptimizationSourcePositionEncoder();
        sourcePositionEncoder.encodeAndInstall(compilation.getDeoptimizationSourcePositions(), runtimeMethodInfo);
    }

    private void patchData(Map<Integer, NativeImagePatcher> patcher, @SuppressWarnings("unused") ObjectConstantsHolder objectConstants) {
        for (DataPatch dataPatch : compilation.getDataPatches()) {
            NativeImagePatcher patch = patcher.get(dataPatch.pcOffset);
            if (dataPatch.reference instanceof DataSectionReference) {
                DataSectionReference ref = (DataSectionReference) dataPatch.reference;
                int pcDisplacement = constantsOffset + ref.getOffset() - dataPatch.pcOffset;
                patch.patch(dataPatch.pcOffset, pcDisplacement, compiledBytes);
            } else if (dataPatch.reference instanceof ConstantReference) {
                ConstantReference ref = (ConstantReference) dataPatch.reference;
                SubstrateObjectConstant refConst = (SubstrateObjectConstant) ref.getConstant();
                objectConstants.add(patch, refConst);
            }
        }
    }

    private int patchCalls(Map<Integer, NativeImagePatcher> patches) {
        /*
         * Patch the direct call instructions. TODO: This is highly x64 specific. Should be
         * rewritten to generic backends.
         */
        Map<Long, Integer> directTargets = new HashMap<>();
        int currentPos = codeSize;
        for (Infopoint infopoint : compilation.getInfopoints()) {
            if (infopoint instanceof Call && ((Call) infopoint).direct) {
                Call call = (Call) infopoint;
                long targetAddress = getTargetCodeAddress(call);
                long pcDisplacement = targetAddress - (code.rawValue() + call.pcOffset);
                if (pcDisplacement != (int) pcDisplacement || testTrampolineJumps) {
                    /*
                     * In case a trampoline jump is need we just "call" the trampoline jump at the
                     * end of the code.
                     */
                    Long destAddr = Long.valueOf(targetAddress);
                    Integer trampolineOffset = directTargets.get(destAddr);
                    if (trampolineOffset == null) {
                        trampolineOffset = currentPos;
                        directTargets.put(destAddr, trampolineOffset);
                        currentPos += TRAMPOLINE_JUMP_SIZE;
                    }
                    pcDisplacement = trampolineOffset - call.pcOffset;
                }
                assert pcDisplacement == (int) pcDisplacement;

                // Patch a PC-relative call.
                patches.get(call.pcOffset).patch(call.pcOffset, (int) pcDisplacement, compiledBytes);
            }
        }
        if (directTargets.size() > 0) {
            /*
             * Insert trampoline jumps. Note that this is only a fail-safe, because usually the code
             * should be within a 32-bit address range.
             */
            currentPos = NumUtil.roundUp(currentPos, 8);
            ByteOrder byteOrder = ConfigurationValues.getTarget().arch.getByteOrder();
            assert byteOrder == ByteOrder.LITTLE_ENDIAN : "Code below assumes little-endian byte order";
            ByteBuffer codeBuffer = ByteBuffer.wrap(compiledBytes).order(byteOrder);
            for (Entry<Long, Integer> entry : directTargets.entrySet()) {
                long targetAddress = entry.getKey();
                int trampolineOffset = entry.getValue();
                // Write the "jmp [rip+offset]" instruction
                codeBuffer.put(trampolineOffset + 0, (byte) 0xff);
                codeBuffer.put(trampolineOffset + 1, (byte) 0x25);
                codeBuffer.putInt(trampolineOffset + 2, currentPos - (trampolineOffset + TRAMPOLINE_JUMP_SIZE));
                // Write the target address
                codeBuffer.putLong(currentPos, targetAddress);
                currentPos += 8;
            }
        }
        return currentPos;
    }

    private long getTargetCodeAddress(Call callInfo) {

        // NOTE that for the moment, we don't make static calls to external
        // (e.g. native) functions.
        // This will change, and we will have to case-split here... but not yet.
        SharedMethod targetMethod = (SharedMethod) callInfo.target;
        long callTargetStart = CodeInfoAccess.absoluteIP(CodeInfoTable.getImageCodeInfo(), targetMethod.getCodeOffsetInImage()).rawValue();

        if (allInstalledCode != null) {
            InstalledCodeBuilder targetInstalledCodeBuilder = allInstalledCode.get(targetMethod);
            if (targetInstalledCodeBuilder != null) {
                SubstrateInstalledCode targetInstalledCode = targetInstalledCodeBuilder.getInstalledCode();
                if (targetInstalledCode != null && targetInstalledCode.isValid()) {
                    callTargetStart = targetInstalledCode.getAddress();
                }
            }
        }
        if (callTargetStart == 0) {
            throw VMError.shouldNotReachHere("target method not compiled: " + targetMethod.format("%H.%n(%p)"));
        }
        return callTargetStart;
    }

    private static Pointer allocateOSMemory(final UnsignedWord size) {
        final Log trace = Log.noopLog();
        trace.string("[SubstrateInstalledCode.allocateAlignedMemory:");
        trace.string("  size: ").unsigned(size);
        final Pointer result = CommittedMemoryProvider.get().allocate(size, WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
        trace.string("  returns: ").hex(result);
        trace.string("]").newline();
        if (result.isNull()) {
            throw new OutOfMemoryError();
        }
        return result;
    }

    private static void freeOSMemory(final Pointer start, final UnsignedWord size) {
        final Log trace = Log.noopLog();
        trace.string("[SubstrateInstalledCode.freeOSMemory:");
        trace.string("  start: ").hex(start);
        trace.string("  size: ").unsigned(size);
        CommittedMemoryProvider.get().free(start, size, WordFactory.unsigned(SubstrateOptions.codeAlignment()), true);
        trace.string("]").newline();
    }
}
