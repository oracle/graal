/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.code.ImageCodeInfo.HostedImageCodeInfo;
import com.oracle.svm.core.code.InstantReferenceAdjuster;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.graal.code.SubstrateDataBuilder;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.code.CompilationInfo;
import com.oracle.svm.hosted.code.CompilationInfoSupport;
import com.oracle.svm.hosted.image.NativeBootImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

public abstract class NativeImageCodeCache {

    public static class Options {
        @Option(help = "Verify that all possible deoptimization entry points have been properly compiled and registered in the metadata")//
        public static final HostedOptionKey<Boolean> VerifyDeoptimizationEntryPoints = new HostedOptionKey<>(false);
    }

    private final NativeImageHeap imageHeap;

    protected final Map<HostedMethod, CompilationResult> compilations;

    protected final NavigableMap<Integer, CompilationResult> compilationsByStart = new TreeMap<>();

    protected final Platform targetPlatform;

    private final DataSection dataSection;

    private final Map<Constant, String> constantReasons = new HashMap<>();

    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap) {
        this(compilations, imageHeap, ImageSingletons.lookup(Platform.class));
    }

    public NativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform) {
        this.compilations = compilations;
        this.imageHeap = imageHeap;
        this.dataSection = new DataSection();
        this.targetPlatform = targetPlatform;
    }

    public abstract int getCodeCacheSize();

    public CompilationResult getCompilationAtOffset(int offset) {
        Entry<Integer, CompilationResult> floor = compilationsByStart.floorEntry(offset);
        if (floor != null) {
            return floor.getValue();
        } else {
            return null;
        }
    }

    public CompilationResult getFirstCompilation() {
        Entry<Integer, CompilationResult> floor = compilationsByStart.ceilingEntry(0);
        if (floor != null) {
            return floor.getValue();
        } else {
            return null;
        }
    }

    public abstract void layoutMethods(DebugContext debug, String imageName, BigBang bb, ForkJoinPool threadPool);

    public void layoutConstants() {
        for (CompilationResult compilation : compilations.values()) {
            for (DataSection.Data data : compilation.getDataSection()) {
                if (data instanceof SubstrateDataBuilder.ObjectData) {
                    JavaConstant constant = ((SubstrateDataBuilder.ObjectData) data).getConstant();
                    constantReasons.put(constant, compilation.getName());
                }
            }

            dataSection.addAll(compilation.getDataSection());

            for (DataPatch patch : compilation.getDataPatches()) {
                if (patch.reference instanceof ConstantReference) {
                    VMConstant constant = ((ConstantReference) patch.reference).getConstant();
                    constantReasons.put(constant, compilation.getName());
                }
            }
        }
        dataSection.close();
    }

    public void addConstantsToHeap() {
        for (DataSection.Data data : dataSection) {
            if (data instanceof SubstrateDataBuilder.ObjectData) {
                JavaConstant constant = ((SubstrateDataBuilder.ObjectData) data).getConstant();
                addConstantToHeap(constant);
            }
        }
        for (CompilationResult compilationResult : compilations.values()) {
            for (DataPatch patch : compilationResult.getDataPatches()) {
                if (patch.reference instanceof ConstantReference) {
                    addConstantToHeap(((ConstantReference) patch.reference).getConstant(), compilationResult.getName());
                }
            }
        }
    }

    private void addConstantToHeap(Constant constant) {
        addConstantToHeap(constant, null);
    }

    private void addConstantToHeap(Constant constant, Object reason) {
        Object obj = SubstrateObjectConstant.asObject(constant);

        if (!imageHeap.getMetaAccess().lookupJavaType(obj.getClass()).getWrapped().isInstantiated()) {
            throw shouldNotReachHere("Non-instantiated type referenced by a compiled method: " + obj.getClass().getName() + "." +
                            (reason != null ? " Method: " + reason : ""));
        }

        imageHeap.addObject(obj, false, constantReasons.get(constant));
    }

    protected int getConstantsSize() {
        return dataSection.getSectionSize();
    }

    public int getAlignedConstantsSize() {
        return ConfigurationValues.getObjectLayout().alignUp(getConstantsSize());
    }

    public void buildRuntimeMetadata(CFunctionPointer firstMethod, UnsignedWord codeSize) {
        // Build run-time metadata.
        FrameInfoCustomization frameInfoCustomization = new FrameInfoCustomization();
        CodeInfoEncoder codeInfoEncoder = new CodeInfoEncoder(frameInfoCustomization);
        for (Entry<HostedMethod, CompilationResult> entry : compilations.entrySet()) {
            final HostedMethod method = entry.getKey();
            final CompilationResult compilation = entry.getValue();
            codeInfoEncoder.addMethod(method, compilation, method.getCodeAddressOffset());
        }

        if (NativeImageOptions.PrintMethodHistogram.getValue()) {
            System.out.println("encoded deopt entry points                 ; " + frameInfoCustomization.numDeoptEntryPoints);
            System.out.println("encoded during call entry points           ; " + frameInfoCustomization.numDuringCallEntryPoints);
        }

        HostedImageCodeInfo imageCodeInfo = CodeInfoTable.getImageCodeCache().getHostedImageCodeInfo();
        codeInfoEncoder.encodeAllAndInstall(imageCodeInfo, new InstantReferenceAdjuster());
        imageCodeInfo.setCodeStart(firstMethod);
        imageCodeInfo.setCodeSize(codeSize);

        if (CodeInfoEncoder.Options.CodeInfoEncoderCounters.getValue()) {
            for (Counter counter : ImageSingletons.lookup(CodeInfoEncoder.Counters.class).group.getCounters()) {
                System.out.println(counter.getName() + " ; " + counter.getValue());
            }
        }

        if (Options.VerifyDeoptimizationEntryPoints.getValue()) {
            /*
             * Missing deoptimization entry points lead to hard-to-debug transient failures, so we
             * want the verification on all the time and not just when assertions are on.
             */
            verifyDeoptEntries(imageCodeInfo);
        }

        assert verifyMethods(codeInfoEncoder, imageCodeInfo);
    }

    private void verifyDeoptEntries(CodeInfo codeInfo) {
        boolean hasError = false;
        List<Entry<AnalysisMethod, Set<Long>>> deoptEntries = new ArrayList<>(CompilationInfoSupport.singleton().getDeoptEntries().entrySet());
        deoptEntries.sort((e1, e2) -> e1.getKey().format("%H.%n(%p)").compareTo(e2.getKey().format("%H.%n(%p)")));

        for (Entry<AnalysisMethod, Set<Long>> entry : deoptEntries) {
            HostedMethod method = imageHeap.getUniverse().lookup(entry.getKey());
            List<Long> encodedBcis = new ArrayList<>(entry.getValue());
            encodedBcis.sort((v1, v2) -> Long.compare(v1, v2));

            for (long encodedBci : encodedBcis) {
                hasError |= verifyDeoptEntry(codeInfo, method, encodedBci);
            }
        }
        if (hasError) {
            VMError.shouldNotReachHere("Verification of deoptimization entry points failed");
        }
    }

    private static boolean verifyDeoptEntry(CodeInfo codeInfo, HostedMethod method, long encodedBci) {
        int deoptOffsetInImage = method.getDeoptOffsetInImage();
        if (deoptOffsetInImage <= 0) {
            return error(method, encodedBci, "entry point method not compiled");
        }

        CodeInfoQueryResult result = new CodeInfoQueryResult();
        long relativeIP = CodeInfoAccess.lookupDeoptimizationEntrypoint(codeInfo, deoptOffsetInImage, encodedBci, result);
        if (relativeIP < 0) {
            return error(method, encodedBci, "entry point not found");
        }
        if (result.getFrameInfo() == null || !result.getFrameInfo().isDeoptEntry() || result.getFrameInfo().getEncodedBci() != encodedBci) {
            return error(method, encodedBci, "entry point found, but wrong property");
        }
        return false;
    }

    private static boolean error(HostedMethod method, long encodedBci, String msg) {
        System.out.println(method.format("%H.%n(%p)") + ", encodedBci " + encodedBci + " (bci " + FrameInfoDecoder.readableBci(encodedBci) + "): " + msg);
        return true;
    }

    private boolean verifyMethods(CodeInfoEncoder codeInfoEncoder, CodeInfo codeInfo) {
        for (Entry<HostedMethod, CompilationResult> entry : compilations.entrySet()) {
            CodeInfoEncoder.verifyMethod(entry.getKey(), entry.getValue(), entry.getKey().getCodeAddressOffset(), codeInfo);
        }
        codeInfoEncoder.verifyFrameInfo(codeInfo);
        return true;
    }

    /*
     * Constants and code objects are all assigned offsets in the heap. Reference constants can
     * refer to other heap objects. TODO: is it true that that all code-->data references go via a
     * Constant? It appears so, but I'm not sure. -srk
     */

    public abstract void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile);

    public abstract void writeCode(RelocatableBuffer buffer);

    public void writeConstants(NativeImageHeapWriter writer, RelocatableBuffer buffer) {
        ByteBuffer bb = buffer.getByteBuffer();
        dataSection.buildDataSection(bb, (position, constant) -> {
            writer.writeReference(buffer, position, SubstrateObjectConstant.asObject(constant), "VMConstant: " + constant);
        });
    }

    public abstract NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache);

    public Path[] getCCInputFiles(Path tempDirectory, String imageName) {
        return new Path[]{tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix())};
    }

    public abstract List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile, boolean onlyGlobal);

    public Map<HostedMethod, CompilationResult> getCompilations() {
        return compilations;
    }

    public void printCompilationResults() {
        System.out.println("--- compiled methods");
        for (Entry<HostedMethod, CompilationResult> entry : compilations.entrySet()) {
            HostedMethod method = entry.getKey();
            CompilationResult result = entry.getValue();
            System.out.format("%8d %5d %s: frame %d\n", method.getCodeAddressOffset(), result.getTargetCodeSize(), method.format("%H.%n(%p)"), result.getTotalFrameSize());
        }
        System.out.println("--- vtables:");
        for (HostedType type : imageHeap.getUniverse().getTypes()) {
            for (int i = 0; i < type.getVTable().length; i++) {
                HostedMethod method = type.getVTable()[i];
                if (method != null) {
                    CompilationResult comp = compilations.get(type.getVTable()[i]);
                    if (comp != null) {
                        System.out.format("%d %s @ %d: %s = 0x%x\n", type.getTypeID(), type.toJavaName(false), i, method.format("%r %n(%p)"), method.getCodeAddressOffset());
                    }
                }
            }
        }
    }

    private static class FrameInfoCustomization extends FrameInfoEncoder.NamesFromMethod {
        int numDeoptEntryPoints;
        int numDuringCallEntryPoints;

        @Override
        protected Class<?> getDeclaringJavaClass(ResolvedJavaMethod method) {
            HostedType type = (HostedType) method.getDeclaringClass();
            assert type.getWrapped().isInTypeCheck() : "Declaring class not marked as used, therefore the DynamicHub is not initialized properly: " + method.format("%H.%n(%p)");
            return type.getJavaClass();
        }

        @Override
        protected boolean shouldStoreMethod() {
            return false;
        }

        @Override
        protected boolean shouldInclude(ResolvedJavaMethod method, Infopoint infopoint) {
            CompilationInfo compilationInfo = ((HostedMethod) method).compilationInfo;
            BytecodeFrame topFrame = infopoint.debugInfo.frame();

            if (isDeoptEntry(method, infopoint)) {
                /* Collect number of entry points for later printing of statistics. */
                if (infopoint instanceof DeoptEntryInfopoint) {
                    numDeoptEntryPoints++;
                } else if (infopoint instanceof Call) {
                    numDuringCallEntryPoints++;
                } else {
                    throw shouldNotReachHere();
                }

                return true;
            }
            BytecodeFrame rootFrame = topFrame;
            while (rootFrame.caller() != null) {
                rootFrame = rootFrame.caller();
            }
            assert rootFrame.getMethod().equals(method);

            boolean isDeoptEntry = compilationInfo.isDeoptEntry(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
            if (infopoint instanceof DeoptEntryInfopoint) {
                assert isDeoptEntry;
                assert topFrame == rootFrame : "Deoptimization target has inlined frame";

                numDeoptEntryPoints++;
                return true;

            }

            if (isDeoptEntry && topFrame.duringCall) {
                assert infopoint instanceof Call;
                assert topFrame == rootFrame : "Deoptimization target has inlined frame";

                numDuringCallEntryPoints++;
                return true;
            }

            for (BytecodeFrame frame = topFrame; frame != null; frame = frame.caller()) {
                if (CompilationInfoSupport.singleton().isFrameInformationRequired(frame.getMethod())) {
                    /*
                     * Somewhere in the inlining hierarchy is a method for which frame information
                     * was explicitly requested. For simplicity, we output frame information for all
                     * methods in the inlining chain.
                     *
                     * We require frame information, for example, for frames that must be visible to
                     * SubstrateStackIntrospection.
                     */
                    return true;
                }
            }

            if (compilationInfo.canDeoptForTesting()) {
                return true;
            }

            return false;
        }

        @Override
        protected boolean isDeoptEntry(ResolvedJavaMethod method, Infopoint infopoint) {
            CompilationInfo compilationInfo = ((HostedMethod) method).compilationInfo;
            BytecodeFrame topFrame = infopoint.debugInfo.frame();

            BytecodeFrame rootFrame = topFrame;
            while (rootFrame.caller() != null) {
                rootFrame = rootFrame.caller();
            }
            assert rootFrame.getMethod().equals(method);

            boolean isDeoptEntry = compilationInfo.isDeoptEntry(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
            if (infopoint instanceof DeoptEntryInfopoint) {
                assert isDeoptEntry;
                assert topFrame == rootFrame : "Deoptimization target has inlined frame";
                return true;
            }
            if (isDeoptEntry && topFrame.duringCall) {
                assert infopoint instanceof Call;
                assert topFrame == rootFrame : "Deoptimization target has inlined frame";
                return true;
            }
            return false;
        }
    }
}
