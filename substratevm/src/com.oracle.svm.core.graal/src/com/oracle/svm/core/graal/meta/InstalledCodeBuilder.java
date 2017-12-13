/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.DeoptimizationSourcePositionEncoder;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.RuntimeMethodInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.graal.code.amd64.AMD64InstructionPatcher;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.heap.PinnedAllocator;
import com.oracle.svm.core.heap.ReferenceMapDecoder;
import com.oracle.svm.core.heap.ReferenceMapEncoder;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
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
    private final Map<SharedMethod, InstalledCodeBuilder> allInstalledCode;
    protected Pointer code;
    private final int codeSize;
    private final int constantsOffset;
    private final InstalledCodeObserver[] codeObservers;

    private SubstrateCompilationResult compilation;
    private byte[] compiledBytes;

    /**
     * Pinned allocation methods used when creating the pointer map and the code chunk infos when
     * code is installed. These object become unpinned when the code is invalidated.
     */
    private final PinnedAllocator metaInfoAllocator;

    private RuntimeMethodInfo runtimeMethodInfo;

    /**
     * The walker for the GC to visit object references in the constants area of the compiled
     * method.
     */
    public static class ConstantsWalker extends ObjectReferenceWalker {

        /**
         * The address of the constants area, which is located right after the compiled code.
         */
        Pointer constantsAddr;

        /**
         * The size of the constants area.
         */
        int constantsSize;

        /**
         * The pointer map for the constants.
         */
        byte[] referenceMapEncoding;
        long referenceMapIndex;

        /**
         * Set to true after everything is set up and GC can operate on the constants area.
         */
        boolean pointerMapValid;

        /** Called by the GC to walk over the object references in the constants-area. */
        @Override
        public boolean walk(final ObjectReferenceVisitor referenceVisitor) {
            if (pointerMapValid) {
                return ReferenceMapDecoder.walkOffsetsFromPointer(constantsAddr, referenceMapEncoding, referenceMapIndex, referenceVisitor);
            }
            return false;
        }

        /** For verification: Does the memory known to this walker contain this pointer? */
        @Override
        public boolean containsPointer(final Pointer p) {
            final boolean atLeast = constantsAddr.belowOrEqual(p);
            final boolean atMost = p.belowThan(constantsAddr.add(constantsSize));
            final boolean result = (atLeast && atMost);
            return result;
        }
    }

    /**
     * The pointer map for the constants area (which is located right after the compiled code).
     */
    private ConstantsWalker constantsWalker;

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
        this.installedCode = installedCode;
        this.allInstalledCode = allInstalledCode;
        this.testTrampolineJumps = testTrampolineJumps;
        this.metaInfoAllocator = Heap.getHeap().createPinnedAllocator();

        DebugContext debug = DebugContext.forCurrentThread();
        try (Indent indent = debug.logAndIndent("create installed code of %s.%s", method.getDeclaringClass().getName(), method.getName())) {
            TargetDescription target = ConfigurationValues.getTarget();

            if (target.arch.getPlatformKind(JavaKind.Object).getSizeInBytes() != 8) {
                throw VMError.shouldNotReachHere("wrong object size");
            }

            int constantsSize = compilation.getDataSection().getSectionSize();
            codeSize = compilation.getTargetCodeSize();
            int tmpConstantsOffset = ObjectLayout.roundUp(codeSize, compilation.getDataSection().getSectionAlignment());
            int tmpMemorySize = tmpConstantsOffset + constantsSize;

            // Allocate executable memory. It contains the compiled code and the constants
            //
            code = allocateOSMemory(WordFactory.unsigned(tmpMemorySize), true);

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
                tmpConstantsOffset = ObjectLayout.roundUp(codeSize + directTargets.size() * TRAMPOLINE_JUMP_SIZE, 8);
                // Add space for the target addresses
                // (which are referenced from the jump instructions)
                tmpConstantsOffset = ObjectLayout.roundUp(tmpConstantsOffset + directTargets.size() * 8, compilation.getDataSection().getSectionAlignment());
                if (tmpConstantsOffset > compiledBytes.length) {
                    compiledBytes = Arrays.copyOf(compiledBytes, tmpConstantsOffset);
                }
                tmpMemorySize = tmpConstantsOffset + constantsSize;

                code = allocateOSMemory(WordFactory.unsigned(tmpMemorySize), true);
            }
            constantsOffset = tmpConstantsOffset;

            codeObservers = ImageSingletons.lookup(InstalledCodeObserverSupport.class).createObservers(debug, method, compilation, code);
        }

    }

    public SubstrateInstalledCode getInstalledCode() {
        return installedCode;
    }

    static class ObjectConstantsHolder {
        final int[] offsets;
        final Object[] values;
        int count;

        final SubstrateReferenceMap referenceMap;

        ObjectConstantsHolder(CompilationResult compilation) {
            /* Conservative estimate on the maximum number of object constants we might have. */
            offsets = new int[compilation.getDataSection().getSectionSize() / FrameAccess.wordSize()];
            values = new Object[offsets.length];
            referenceMap = new SubstrateReferenceMap();
        }

        void add(int offset, Object value) {
            offsets[count] = offset;
            values[count] = value;
            count++;

            assert offset % FrameAccess.wordSize() == 0;
            referenceMap.markReferenceAtIndex(offset / FrameAccess.wordSize());
        }
    }

    public void install() {
        this.installOperation();
    }

    @SuppressWarnings("try")
    private void installOperation() {
        AMD64InstructionPatcher patcher = new AMD64InstructionPatcher(compilation);
        patchData(patcher);

        int updatedCodeSize = patchCalls(patcher);
        assert updatedCodeSize <= constantsOffset;

        // Store the compiled code
        for (int index = 0; index < updatedCodeSize; index++) {
            code.writeByte(index, compiledBytes[index]);
        }

        /* Primitive constants are written directly to the code memory. */
        ByteBuffer constantsBuffer = SubstrateUtil.wrapAsByteBuffer(code.add(constantsOffset), compilation.getDataSection().getSectionSize());
        /*
         * Object constants are stored in an Object[] array first, because we have to be careful
         * that they are always exposed as roots to the GC.
         */
        ObjectConstantsHolder objectConstants = new ObjectConstantsHolder(compilation);

        compilation.getDataSection().buildDataSection(constantsBuffer, constant -> {
            objectConstants.add(constantsBuffer.position(), KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(constant), Object.class));
        });

        // Open the PinnedAllocator for the meta-information.
        metaInfoAllocator.open();
        try {
            runtimeMethodInfo = metaInfoAllocator.newInstance(RuntimeMethodInfo.class);
            constantsWalker = metaInfoAllocator.newInstance(ConstantsWalker.class);

            ReferenceMapEncoder encoder = new ReferenceMapEncoder();
            encoder.add(objectConstants.referenceMap);
            constantsWalker.referenceMapEncoding = encoder.encodeAll(metaInfoAllocator);
            constantsWalker.referenceMapIndex = encoder.lookupEncoding(objectConstants.referenceMap);
            constantsWalker.constantsAddr = code.add(constantsOffset);
            constantsWalker.constantsSize = compilation.getDataSection().getSectionSize();
            Heap.getHeap().getGC().registerObjectReferenceWalker(constantsWalker);

            /*
             * We now have the constantsWalker initialized and registered, but it is still inactive.
             * Writing the actual object constants to the code memory needs to be atomic regarding
             * to GC. After everything is written, we activate the constantsWalker.
             */
            try (NoAllocationVerifier verifier = NoAllocationVerifier.factory("InstalledCodeBuilder.install")) {
                writeObjectConstantsToCode(objectConstants);
            }

            createCodeChunkInfos();

            InstalledCodeObserver.InstalledCodeObserverHandle[] observerHandles = InstalledCodeObserverSupport.installObservers(codeObservers, metaInfoAllocator);

            runtimeMethodInfo.setData((CodePointer) code, WordFactory.unsigned(codeSize), installedCode, constantsWalker, metaInfoAllocator, observerHandles);
        } finally {
            metaInfoAllocator.close();
        }

        CodeInfoTable.getRuntimeCodeCache().addMethod(runtimeMethodInfo);

        /*
         * This call makes the new code visible, i.e., other threads can start executing it
         * immediately. So all metadata must be registered at this point.
         */
        installedCode.setAddress(code.rawValue(), method);

        compilation = null;
    }

    @Uninterruptible(reason = "Operates on raw pointers to objects")
    private void writeObjectConstantsToCode(ObjectConstantsHolder objectConstants) {
        Pointer constantsStart = code.add(constantsOffset);
        for (int i = 0; i < objectConstants.count; i++) {
            constantsStart.writeObject(objectConstants.offsets[i], objectConstants.values[i]);
        }
        /* From now on the constantsWalker will operate on the constants area. */
        constantsWalker.pointerMapValid = true;
    }

    private void createCodeChunkInfos() {
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(new FrameInfoEncoder.NamesFromImage(), metaInfoAllocator);
        codeInfoEncoder.addMethod(method, compilation, 0);
        codeInfoEncoder.encodeAll();
        codeInfoEncoder.install(runtimeMethodInfo);
        assert codeInfoEncoder.verifyMethod(compilation, 0);

        DeoptimizationSourcePositionEncoder sourcePositionEncoder = new DeoptimizationSourcePositionEncoder(metaInfoAllocator);
        sourcePositionEncoder.encode(compilation.getDeoptimzationSourcePositions());
        sourcePositionEncoder.install(runtimeMethodInfo);
    }

    private void patchData(AMD64InstructionPatcher patcher) {
        for (DataPatch dataPatch : compilation.getDataPatches()) {
            DataSectionReference ref = (DataSectionReference) dataPatch.reference;
            int pcDisplacement = constantsOffset + ref.getOffset() - dataPatch.pcOffset;

            patcher.findPatchData(dataPatch.pcOffset, pcDisplacement).apply(compiledBytes);
        }
    }

    private int patchCalls(AMD64InstructionPatcher patcher) {
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
                patcher.findPatchData(call.pcOffset, (int) pcDisplacement).apply(compiledBytes);
            }
        }
        if (directTargets.size() > 0) {
            /*
             * Insert trampoline jumps. Note that this is only a fail-safe, because usually the code
             * should be within a 32-bit address range.
             */
            currentPos = ObjectLayout.roundUp(currentPos, 8);
            ByteBuffer codeBuffer = ByteBuffer.wrap(compiledBytes).order(ConfigurationValues.getTarget().arch.getByteOrder());
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
        long callTargetStart = CodeInfoTable.getImageCodeCache().absoluteIP(targetMethod.getCodeOffsetInImage()).rawValue();

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

    /** A tracing wrapper around getOSInterface().allocateVirtualMemory. */
    protected Pointer allocateOSMemory(final UnsignedWord size, final boolean executable) {
        final Log trace = Log.noopLog();
        trace.string("[SubstrateInstalledCode.allocateAlignedMemory:");
        trace.string("  size: ").unsigned(size);
        trace.string("  executable: ").bool(executable);
        final Pointer result = ConfigurationValues.getOSInterface().allocateVirtualMemory(size, executable);
        trace.string("  returns: ").hex(result);
        trace.string("]").newline();
        return result;
    }

    /** A tracing wrapper around getOSInterface().freeVirtualMemory. */
    protected void freeOSMemory(final Pointer start, final UnsignedWord size) {
        final Log trace = Log.noopLog();
        trace.string("[SubstrateInstalledCode.freeOSMemory:");
        trace.string("  start: ").hex(start);
        trace.string("  size: ").unsigned(size);
        ConfigurationValues.getOSInterface().freeVirtualMemory(start, size);
        trace.string("]").newline();
    }
}
