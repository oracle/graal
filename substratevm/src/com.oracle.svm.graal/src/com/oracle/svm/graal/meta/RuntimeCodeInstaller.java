/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.DeoptimizationSourcePositionEncoder;
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

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaKind;

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
        this.tier = compilation.getName().endsWith(TruffleCompiler.FIRST_TIER_COMPILATION_SUFFIX) ? TruffleCompiler.FIRST_TIER_INDEX : TruffleCompiler.LAST_TIER_INDEX;
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
                dataOffset = UnsignedUtils.safeToInt(UnsignedUtils.roundUp(WordFactory.unsigned(dataOffset), CommittedMemoryProvider.get().getGranularity()));
            }
            codeAndDataMemorySize = UnsignedUtils.safeToInt(UnsignedUtils.roundUp(WordFactory.unsigned(dataOffset + dataSize), CommittedMemoryProvider.get().getGranularity()));
            code = allocateCodeMemory(codeAndDataMemorySize);
            compiledBytes = compilation.getTargetCode();

            if (!RuntimeCodeCache.Options.WriteableCodeCache.getValue()) {
                makeCodeMemoryWriteableNonExecutable(code.add(dataOffset), codeAndDataMemorySize - dataOffset);
            }

            codeObservers = ImageSingletons.lookup(InstalledCodeObserverSupport.class).createObservers(debug, method, compilation, code, codeSize);
        }
    }

    private static class ObjectConstantsHolder {
        final SubstrateReferenceMap referenceMap;
        final int[] offsets;
        final int[] lengths;
        final SubstrateObjectConstant[] constants;
        int count;

        ObjectConstantsHolder(CompilationResult compilation) {
            /* Conservative estimate on the maximum number of object constants we might have. */
            int maxDataRefs = compilation.getDataSection().getSectionSize() / ConfigurationValues.getObjectLayout().getReferenceSize();
            int maxCodeRefs = compilation.getDataPatches().size();
            int maxTotalRefs = maxDataRefs + maxCodeRefs;
            offsets = new int[maxTotalRefs];
            lengths = new int[maxTotalRefs];
            constants = new SubstrateObjectConstant[maxTotalRefs];
            referenceMap = new SubstrateReferenceMap();
        }

        void add(int offset, int length, SubstrateObjectConstant constant) {
            assert constant.isCompressed() == ReferenceAccess.singleton().haveCompressedReferences() : "Object reference constants in code must be compressed";
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
                patches.put(codeAnnotation.getPosition(), (NativeImagePatcher) codeAnnotation);
            }
        }
        patchData(patches, objectConstants);

        // Store the compiled code
        for (int index = 0; index < codeSize; index++) {
            code.writeByte(index, compiledBytes[index]);
        }

        // remove write access from code
        if (!RuntimeCodeCache.Options.WriteableCodeCache.getValue()) {
            makeCodeMemoryReadOnly(code, codeSize);
        }

        /* Write primitive constants to the buffer, record object constants with offsets */
        ByteBuffer dataBuffer = CTypeConversion.asByteBuffer(code.add(dataOffset), compilation.getDataSection().getSectionSize());
        compilation.getDataSection().buildDataSection(dataBuffer, (position, constant) -> {
            objectConstants.add(dataOffset + position,
                            ConfigurationValues.getObjectLayout().getReferenceSize(),
                            (SubstrateObjectConstant) constant);
        });

        NonmovableArray<InstalledCodeObserverHandle> observerHandles = InstalledCodeObserverSupport.installObservers(codeObservers);
        RuntimeCodeInfoAccess.initialize(codeInfo, code, codeSize, dataOffset, dataSize, codeAndDataMemorySize, tier, observerHandles, false);

        CodeReferenceMapEncoder encoder = new CodeReferenceMapEncoder();
        encoder.add(objectConstants.referenceMap);
        RuntimeCodeInfoAccess.setCodeObjectConstantsInfo(codeInfo, encoder.encodeAll(), encoder.lookupEncoding(objectConstants.referenceMap));
        patchDirectObjectConstants(objectConstants, codeInfo, adjuster);

        createCodeChunkInfos(codeInfo, adjuster);
        compilation = null;
    }

    @Uninterruptible(reason = "Must be atomic with regard to garbage collection.")
    private void patchDirectObjectConstants(ObjectConstantsHolder objectConstants, CodeInfo runtimeMethodInfo, ReferenceAdjuster adjuster) {
        for (int i = 0; i < objectConstants.count; i++) {
            SubstrateObjectConstant constant = objectConstants.constants[i];
            adjuster.setConstantTargetAt(code.add(objectConstants.offsets[i]), objectConstants.lengths[i], constant);
        }
        CodeInfoAccess.setState(runtimeMethodInfo, CodeInfo.STATE_CODE_CONSTANTS_LIVE);
        if (!SubstrateUtil.HOSTED) {
            // after patching all the object data, notify the GC
            Heap.getHeap().getRuntimeCodeInfoGCSupport().registerCodeConstants(runtimeMethodInfo);
        }
    }

    private void createCodeChunkInfos(CodeInfo runtimeMethodInfo, ReferenceAdjuster adjuster) {
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(new FrameInfoEncoder.NamesFromImage());
        codeInfoEncoder.addMethod(method, compilation, 0);
        codeInfoEncoder.encodeAllAndInstall(runtimeMethodInfo, adjuster);

        assert !adjuster.isFinished() || CodeInfoEncoder.verifyMethod(method, compilation, 0, runtimeMethodInfo);
        assert !adjuster.isFinished() || codeInfoEncoder.verifyFrameInfo(runtimeMethodInfo);

        DeoptimizationSourcePositionEncoder sourcePositionEncoder = new DeoptimizationSourcePositionEncoder();
        sourcePositionEncoder.encodeAndInstall(compilation.getDeoptimizationSourcePositions(), runtimeMethodInfo, adjuster);
    }

    private void patchData(Map<Integer, NativeImagePatcher> patcher, @SuppressWarnings("unused") ObjectConstantsHolder objectConstants) {
        for (DataPatch dataPatch : compilation.getDataPatches()) {
            NativeImagePatcher patch = patcher.get(dataPatch.pcOffset);
            if (dataPatch.reference instanceof DataSectionReference) {
                DataSectionReference ref = (DataSectionReference) dataPatch.reference;
                int pcDisplacement = dataOffset + ref.getOffset() - dataPatch.pcOffset;
                patch.patchCode(pcDisplacement, compiledBytes);
            } else if (dataPatch.reference instanceof ConstantReference) {
                ConstantReference ref = (ConstantReference) dataPatch.reference;
                SubstrateObjectConstant refConst = (SubstrateObjectConstant) ref.getConstant();
                objectConstants.add(patch.getOffset(), patch.getLength(), refConst);
            }
        }
    }
}
