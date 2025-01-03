/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.DeoptimizationSourcePositionEncoder;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.InstantReferenceAdjuster;
import com.oracle.svm.core.code.ReferenceAdjuster;
import com.oracle.svm.core.code.RuntimeCodeCache;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.graal.code.NativeImagePatcher;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.heap.CodeReferenceMapEncoder;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.CodeAnnotation;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Handles the installation of runtime-compiled code, allocating memory for code, data, and metadata
 * and patching call and jump targets, primitive constants, and object constants.
 */
public class RuntimeCodeInstaller extends AbstractRuntimeCodeInstaller {

    /** Installs the code in the current isolate, in a single step. */
    public static void install(SharedRuntimeMethod method, CompilationResult compilation, SubstrateInstalledCode installedCode) {
        new RuntimeCodeInstaller(method, compilation).doInstall(installedCode);
    }

    protected final SharedRuntimeMethod method;
    private final int tier;
    private SubstrateCompilationResult compilation;
    private final DebugContext debug;

    private Pointer code;
    private int codeSize;
    private int dataOffset;
    private int dataSize;
    private int codeAndDataMemorySize;
    private InstalledCodeObserver[] codeObservers;
    protected byte[] compiledBytes;

    protected RuntimeCodeInstaller(SharedRuntimeMethod method, CompilationResult compilation) {
        this.method = method;
        this.compilation = (SubstrateCompilationResult) compilation;
        this.tier = compilation.getName().endsWith(TruffleCompilerImpl.FIRST_TIER_COMPILATION_SUFFIX) ? TruffleCompilerImpl.FIRST_TIER_INDEX : TruffleCompilerImpl.LAST_TIER_INDEX;
        this.debug = new DebugContext.Builder(RuntimeOptionValues.singleton()).build();
    }

    @SuppressWarnings("try")
    private void prepareCodeMemory() {
        try (Indent indent = debug.logAndIndent("create installed code of %s.%s", method.getDeclaringClass().getName(), method.getName())) {
            TargetDescription target = ConfigurationValues.getTarget();

            if (target.arch.getPlatformKind(JavaKind.Object).getSizeInBytes() != 8) {
                throw VMError.shouldNotReachHere("wrong object size");
            }

            codeSize = compilation.getTargetCodeSize();
            dataSize = compilation.getDataSection().getSectionSize();
            dataOffset = NumUtil.roundUp(codeSize, compilation.getDataSection().getSectionAlignment());
            if (!RuntimeCodeCache.Options.WriteableCodeCache.getValue()) {
                // round up for readonly code cache so that the data section can remain writeable
                dataOffset = UnsignedUtils.safeToInt(UnsignedUtils.roundUp(Word.unsigned(dataOffset), CommittedMemoryProvider.get().getGranularity()));
            }
            codeAndDataMemorySize = UnsignedUtils.safeToInt(UnsignedUtils.roundUp(Word.unsigned(dataOffset + dataSize), CommittedMemoryProvider.get().getGranularity()));

            code = allocateCodeMemory(codeAndDataMemorySize);
            compiledBytes = compilation.getTargetCode();

            if (RuntimeCodeCache.Options.WriteableCodeCache.getValue()) {
                UnsignedWord alignedAfterCodeOffset = UnsignedUtils.roundUp(Word.unsigned(codeSize), CommittedMemoryProvider.get().getGranularity());
                assert alignedAfterCodeOffset.belowOrEqual(codeAndDataMemorySize);

                makeCodeMemoryExecutableWritable(code, alignedAfterCodeOffset);
            }

            codeObservers = ImageSingletons.lookup(InstalledCodeObserverSupport.class).createObservers(debug, method, compilation, code, codeSize);
        }
    }

    private static class ObjectConstantsHolder {
        final SubstrateReferenceMap referenceMap;
        final int[] offsets;
        final int[] lengths;
        final JavaConstant[] constants;
        int count;

        ObjectConstantsHolder(CompilationResult compilation) {
            /* Conservative estimate on the maximum number of object constants we might have. */
            int maxDataRefs = compilation.getDataSection().getSectionSize() / ConfigurationValues.getObjectLayout().getReferenceSize();
            int maxCodeRefs = compilation.getDataPatches().size();
            int maxTotalRefs = maxDataRefs + maxCodeRefs;
            offsets = new int[maxTotalRefs];
            lengths = new int[maxTotalRefs];
            constants = new JavaConstant[maxTotalRefs];
            referenceMap = new SubstrateReferenceMap();
        }

        void add(int offset, int length, JavaConstant constant) {
            assert ((CompressibleConstant) constant).isCompressed() == ReferenceAccess.singleton().haveCompressedReferences() : "Object reference constants in code must be compressed";
            offsets[count] = offset;
            lengths[count] = length;
            constants[count] = constant;
            referenceMap.markReferenceAtOffset(offset, true);
            count++;
        }
    }

    private void doInstall(SubstrateInstalledCode installedCode) {
        ReferenceAdjuster adjuster = new InstantReferenceAdjuster();

        // A freshly allocated CodeInfo object is protected from the GC until the tether is set.
        CodeInfo codeInfo = RuntimeCodeInfoAccess.allocateMethodInfo();
        doPrepareInstall(adjuster, codeInfo);
        doInstallPrepared(method, codeInfo, installedCode);
    }

    protected void doPrepareInstall(ReferenceAdjuster adjuster, CodeInfo codeInfo) {
        for (Infopoint infopoint : compilation.getInfopoints()) {
            VMError.guarantee(!(infopoint instanceof Call) || !((Call) infopoint).direct,
                            "No direct calls permitted: patching of runtime-compiled code intentionally not supported");
        }

        prepareCodeMemory();

        /*
         * Object reference constants are stored in this holder first, then written and made visible
         * in a single step that is atomic regarding to GC.
         */
        ObjectConstantsHolder objectConstants = new ObjectConstantsHolder(compilation);

        // Build an index of PatchingAnnotations
        Map<Integer, NativeImagePatcher> patches = new HashMap<>();
        for (CodeAnnotation codeAnnotation : compilation.getCodeAnnotations()) {
            if (codeAnnotation instanceof NativeImagePatcher) {
                NativeImagePatcher priorValue = patches.put(codeAnnotation.getPosition(), (NativeImagePatcher) codeAnnotation);
                VMError.guarantee(priorValue == null, "Registering two patchers for same position.");
            }
        }
        int numPatchesHandled = patchData(patches, objectConstants);
        VMError.guarantee(numPatchesHandled == patches.size(), "Not all patches applied.");

        // Store the compiled code
        for (int index = 0; index < codeSize; index++) {
            code.writeByte(index, compiledBytes[index]);
        }

        // remove write access from code
        if (!RuntimeCodeCache.Options.WriteableCodeCache.getValue()) {
            makeCodeMemoryExecutableReadOnly(code, Word.unsigned(codeSize));
        }

        /* Write primitive constants to the buffer, record object constants with offsets */
        ByteBuffer dataBuffer = CTypeConversion.asByteBuffer(code.add(dataOffset), compilation.getDataSection().getSectionSize());
        compilation.getDataSection().buildDataSection(dataBuffer, (position, constant) -> {
            objectConstants.add(dataOffset + position,
                            ConfigurationValues.getObjectLayout().getReferenceSize(),
                            (SubstrateObjectConstant) constant);
        });

        int entryPointOffset = 0;

        /* If the code starts after an offset, adjust the entry point accordingly */
        for (CompilationResult.CodeMark mark : compilation.getMarks()) {
            if (mark.id == SubstrateBackend.SubstrateMarkId.PROLOGUE_START) {
                assert entryPointOffset == 0;
                entryPointOffset = mark.pcOffset;
            }
        }

        NonmovableArray<InstalledCodeObserverHandle> observerHandles = InstalledCodeObserverSupport.installObservers(codeObservers);
        RuntimeCodeInfoAccess.initialize(codeInfo, code, entryPointOffset, codeSize, dataOffset, dataSize, codeAndDataMemorySize, tier, observerHandles, false);

        CodeReferenceMapEncoder encoder = new CodeReferenceMapEncoder();
        encoder.add(objectConstants.referenceMap);
        RuntimeCodeInfoAccess.setCodeObjectConstantsInfo(codeInfo, encoder.encodeAll(), encoder.lookupEncoding(objectConstants.referenceMap));
        ImageSingletons.lookup(CodeInfoEncoder.Counters.class).addToReferenceMapSize(encoder.getEncodingSize());
        patchDirectObjectConstants(objectConstants, codeInfo, adjuster);

        createCodeChunkInfos(codeInfo, adjuster);

        compilation = null;
    }

    @Uninterruptible(reason = "Must be atomic with regard to garbage collection.")
    private void patchDirectObjectConstants(ObjectConstantsHolder objectConstants, CodeInfo runtimeMethodInfo, ReferenceAdjuster adjuster) {
        for (int i = 0; i < objectConstants.count; i++) {
            JavaConstant constant = objectConstants.constants[i];
            adjuster.setConstantTargetAt(code.add(objectConstants.offsets[i]), objectConstants.lengths[i], constant);
        }
        CodeInfoAccess.setState(runtimeMethodInfo, CodeInfo.STATE_CODE_CONSTANTS_LIVE);
        if (!SubstrateUtil.HOSTED) {
            // after patching all the object data, notify the GC
            Heap.getHeap().getRuntimeCodeInfoGCSupport().registerCodeConstants(runtimeMethodInfo);
        }
    }

    private void createCodeChunkInfos(CodeInfo runtimeMethodInfo, ReferenceAdjuster adjuster) {
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(new RuntimeFrameInfoCustomization(), new CodeInfoEncoder.Encoders(false, null));
        codeInfoEncoder.addMethod(method, compilation, 0, compilation.getTargetCodeSize());
        Runnable noop = () -> {
        };
        codeInfoEncoder.encodeAllAndInstall(runtimeMethodInfo, adjuster, noop);

        assert !adjuster.isFinished() || CodeInfoEncoder.verifyMethod(method, compilation, 0, compilation.getTargetCodeSize(), runtimeMethodInfo, FrameInfoDecoder.SubstrateConstantAccess);
        assert !adjuster.isFinished() || codeInfoEncoder.verifyFrameInfo(runtimeMethodInfo);

        DeoptimizationSourcePositionEncoder sourcePositionEncoder = new DeoptimizationSourcePositionEncoder();
        sourcePositionEncoder.encodeAndInstall(compilation.getDeoptimizationSourcePositions(), runtimeMethodInfo, adjuster);
    }

    private int patchData(Map<Integer, NativeImagePatcher> patcher, ObjectConstantsHolder objectConstants) {
        int patchesHandled = 0;
        HashSet<Integer> patchedOffsets = new HashSet<>();
        for (DataPatch dataPatch : compilation.getDataPatches()) {
            NativeImagePatcher patch = patcher.get(dataPatch.pcOffset);
            boolean noPriorMatch = patchedOffsets.add(dataPatch.pcOffset);
            VMError.guarantee(noPriorMatch, "Patching same offset twice.");
            patchesHandled++;
            if (dataPatch.reference instanceof DataSectionReference ref) {
                int pcDisplacement = dataOffset + ref.getOffset() - dataPatch.pcOffset;
                patch.patchCode(code.rawValue(), pcDisplacement, compiledBytes);
            } else if (dataPatch.reference instanceof ConstantReference ref) {
                SubstrateObjectConstant refConst = (SubstrateObjectConstant) ref.getConstant();
                objectConstants.add(patch.getOffset(), patch.getLength(), refConst);
            } else {
                throw VMError.shouldNotReachHere("Unhandled data patch.");
            }
        }
        return patchesHandled;
    }

    private static final class RuntimeFrameInfoCustomization extends FrameInfoEncoder.SourceFieldsFromImage {
        @Override
        protected boolean storeDeoptTargetMethod() {
            return true;
        }

        @Override
        protected boolean includeLocalValues(ResolvedJavaMethod method, Infopoint infopoint, boolean isDeoptEntry) {
            return true;
        }

        @Override
        protected boolean isDeoptEntry(ResolvedJavaMethod method, CompilationResult compilation, Infopoint infopoint) {
            return false;
        }
    }
}
