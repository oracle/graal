/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.hosted.image.NativeBootImage.RWDATA_CGLOBALS_PARTITION_OFFSET;
import static org.graalvm.compiler.core.llvm.LLVMUtils.FALSE;
import static org.graalvm.compiler.core.llvm.LLVMUtils.TRUE;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.LLVM;
import org.bytedeco.javacpp.LLVM.LLVMMemoryBufferRef;
import org.bytedeco.javacpp.LLVM.LLVMObjectFileRef;
import org.bytedeco.javacpp.LLVM.LLVMSectionIteratorRef;
import org.bytedeco.javacpp.LLVM.LLVMSymbolIteratorRef;
import org.bytedeco.javacpp.Pointer;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.image.NativeBootImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.ExceptionHandler;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;

@Platforms(Platform.HOSTED_ONLY.class)
public class LLVMNativeImageCodeCache extends NativeImageCodeCache {
    private static final int BATCH_SIZE = 1000;

    private String bitcodeFileName;
    private long codeSize = 0L;
    private Map<String, Integer> textSymbolOffsets = new HashMap<>();
    private Map<Integer, String> offsetToSymbolMap = new TreeMap<>();
    private LLVMStackMapInfo info;
    private HostedMethod firstMethod;

    public LLVMNativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform) {
        super(compilations, imageHeap, targetPlatform);
    }

    @Override
    public int getCodeCacheSize() {
        return 0;
    }

    @Override
    @SuppressWarnings("try")
    public void layoutMethods(DebugContext debug, String imageName) {
        try (Indent indent = debug.logAndIndent("layout methods")) {

            // Compile all methods.
            byte[] bytes;
            try {
                Path basePath = Files.createTempDirectory("native-image-llvm");
                Path outputPath = writeBitcode(debug, basePath, imageName);
                bitcodeFileName = outputPath.toString();
                bytes = Files.readAllBytes(outputPath);
            } catch (IOException e) {
                throw new GraalError(e);
            }

            try (StopTimer t = new Timer(imageName, "(stackmap)").start()) {
                LLVMMemoryBufferRef buffer = LLVM.LLVMCreateMemoryBufferWithMemoryRangeCopy(new BytePointer(bytes), bytes.length, new BytePointer(""));
                LLVMObjectFileRef objectFile = LLVM.LLVMCreateObjectFile(buffer);

                LLVMSectionIteratorRef sectionIterator;
                for (sectionIterator = LLVM.LLVMGetSections(objectFile); LLVM.LLVMIsSectionIteratorAtEnd(objectFile, sectionIterator) == FALSE; LLVM.LLVMMoveToNextSection(sectionIterator)) {
                    BytePointer sectionNamePointer = LLVM.LLVMGetSectionName(sectionIterator);
                    String sectionName = (sectionNamePointer != null) ? sectionNamePointer.getString() : "";
                    if (sectionName.startsWith(SectionName.TEXT.getFormatDependentName(ObjectFile.getNativeFormat()))) {
                        readTextSection(sectionIterator, objectFile);
                    } else if (sectionName.startsWith(SectionName.LLVM_STACKMAPS.getFormatDependentName(ObjectFile.getNativeFormat()))) {
                        readStackMapSection(sectionIterator);
                    }
                }
                assert codeSize > 0L;
                assert info != null;

                LLVM.LLVMDisposeSectionIterator(sectionIterator);

                readStackMap();

                buildRuntimeMetadata(MethodPointer.factory(firstMethod), WordFactory.signed(codeSize));
            }
        }
    }

    private void readTextSection(LLVMSectionIteratorRef sectionIterator, LLVMObjectFileRef objectFile) {
        codeSize = LLVM.LLVMGetSectionSize(sectionIterator);
        long sectionAddress = LLVM.LLVMGetSectionAddress(sectionIterator);

        LLVMSymbolIteratorRef symbolIterator;
        for (symbolIterator = LLVM.LLVMGetSymbols(objectFile); LLVM.LLVMIsSymbolIteratorAtEnd(objectFile, symbolIterator) == FALSE; LLVM.LLVMMoveToNextSymbol(symbolIterator)) {
            if (LLVM.LLVMGetSectionContainsSymbol(sectionIterator, symbolIterator) == TRUE) {
                int offset = NumUtil.safeToInt(LLVM.LLVMGetSymbolAddress(symbolIterator) - sectionAddress);
                String symbolName = LLVM.LLVMGetSymbolName(symbolIterator).getString();
                textSymbolOffsets.put(symbolName, offset);
                offsetToSymbolMap.put(offset, symbolName);
            }
        }
        LLVM.LLVMDisposeSymbolIterator(symbolIterator);
    }

    private void readStackMapSection(LLVMSectionIteratorRef sectionIterator) {
        Pointer stackMap = LLVM.LLVMGetSectionContents(sectionIterator).limit(LLVM.LLVMGetSectionSize(sectionIterator));
        info = new LLVMStackMapInfo(stackMap.asByteBuffer());
    }

    private void readStackMap() {
        String symbolPrefix = (ObjectFile.getNativeFormat() == ObjectFile.Format.MACH_O) ? "_" : "";
        List<Integer> sortedMethodOffsets = textSymbolOffsets.values().stream().distinct().sorted().collect(Collectors.toList());
        Integer gcRegisterOffset = textSymbolOffsets.get(symbolPrefix + "__svm_gc_register");
        if (gcRegisterOffset != null) {
            sortedMethodOffsets.remove(gcRegisterOffset);
        }
        textSymbolOffsets.forEach((symbol, offset) -> {
            if (symbol.startsWith(symbolPrefix + "asm_")) {
                sortedMethodOffsets.remove(offset);
            }
        });

        final FileWriter stackMapDump;
        if (LLVMOptions.DumpLLVMStackMap.hasBeenSet()) {
            try {
                stackMapDump = new FileWriter(LLVMOptions.DumpLLVMStackMap.getValue());
                stackMapDump.write("Offsets\n=======\n");
                for (int offset : sortedMethodOffsets) {
                    String methodName = offsetToSymbolMap.get(offset);
                    stackMapDump.write("[" + offset + "] " + methodName + "\n");
                }
                stackMapDump.write("\nPatchpoints\n===========\n");
            } catch (IOException e) {
                throw shouldNotReachHere();
            }
        } else {
            stackMapDump = null;
        }

        sortedMethodOffsets.add(NumUtil.safeToInt(codeSize));
        compilations.entrySet().parallelStream().forEach(entry -> {
            HostedMethod method = entry.getKey();
            String methodSymbolName = symbolPrefix + SubstrateUtil.uniqueShortName(method);
            assert (textSymbolOffsets.containsKey(methodSymbolName));

            int offset = textSymbolOffsets.get(methodSymbolName);

            CompilationResult compilation = entry.getValue();
            long startPatchpointID = compilation.getInfopoints().stream().filter(ip -> ip.reason == InfopointReason.METHOD_START).findFirst()
                            .orElseThrow(() -> new GraalError("no method start infopoint: " + methodSymbolName)).pcOffset;
            compilation.setTotalFrameSize(NumUtil.safeToInt(info.getFunctionStackSize(startPatchpointID) + FrameAccess.returnAddressSize()));

            int nextFunctionStartOffset = sortedMethodOffsets.get(sortedMethodOffsets.indexOf(offset) + 1);
            int functionSize = nextFunctionStartOffset - offset;
            compilation.setTargetCode(null, functionSize);
            method.setCodeAddressOffset(offset);

            StringBuilder patchpointsDump = null;
            if (LLVMOptions.DumpLLVMStackMap.hasBeenSet()) {
                patchpointsDump = new StringBuilder();
                patchpointsDump.append(methodSymbolName);
                patchpointsDump.append(" [");
                patchpointsDump.append(offset);
                patchpointsDump.append("..");
                patchpointsDump.append(functionSize);
                patchpointsDump.append("]\n");
            }

            List<Infopoint> newInfopoints = new ArrayList<>();
            for (Infopoint infopoint : compilation.getInfopoints()) {
                if (infopoint instanceof Call) {
                    Call call = (Call) infopoint;

                    /* Optimizations might have duplicated some calls. */
                    for (int actualPcOffset : info.getPatchpointOffsets(call.pcOffset)) {
                        SubstrateReferenceMap referenceMap = new SubstrateReferenceMap();
                        info.forEachStatepointOffset(call.pcOffset, actualPcOffset, (o, b) -> referenceMap.markReferenceAtOffset(o, b, SubstrateOptions.SpawnIsolates.getValue()));
                        call.debugInfo.setReferenceMap(referenceMap);

                        if (LLVMOptions.DumpLLVMStackMap.hasBeenSet()) {
                            patchpointsDump.append("  [");
                            patchpointsDump.append(actualPcOffset);
                            patchpointsDump.append("] -> ");
                            patchpointsDump.append(call.target != null ? ((HostedMethod) call.target).format("%H.%n") : "???");
                            patchpointsDump.append(" (");
                            patchpointsDump.append(call.pcOffset);
                            patchpointsDump.append(") ");
                            referenceMap.dump(patchpointsDump);
                            patchpointsDump.append("\n");
                        }

                        newInfopoints.add(new Call(call.target, actualPcOffset, call.size, call.direct, call.debugInfo));
                    }
                }
            }

            compilation.clearInfopoints();

            newInfopoints.forEach(compilation::addInfopoint);

            Map<Integer, Integer> newExceptionHandlers = new HashMap<>();
            for (ExceptionHandler handler : compilation.getExceptionHandlers()) {
                for (int actualPCOffset : info.getPatchpointOffsets(handler.pcOffset)) {
                    assert handler.handlerPos == startPatchpointID;
                    int handlerOffset = info.getAllocaOffset(handler.handlerPos);
                    assert handlerOffset >= 0 && handlerOffset < info.getFunctionStackSize(startPatchpointID);

                    if (LLVMOptions.DumpLLVMStackMap.hasBeenSet()) {
                        patchpointsDump.append("  {");
                        patchpointsDump.append(actualPCOffset);
                        patchpointsDump.append("} -> ");
                        patchpointsDump.append(handlerOffset);
                        patchpointsDump.append("\n");
                    }

                    /*
                     * handlerPos is the position of the setjmp buffer relative to the stack
                     * pointer, plus 1 to avoid having 0 as an offset.
                     */
                    newExceptionHandlers.put(actualPCOffset, actualPCOffset + handlerOffset + 1);
                }
            }

            compilation.clearExceptionHandlers();

            newExceptionHandlers.forEach(compilation::recordExceptionHandler);

            if (LLVMOptions.DumpLLVMStackMap.hasBeenSet()) {
                try {
                    stackMapDump.write(patchpointsDump.toString());
                } catch (IOException e) {
                    throw shouldNotReachHere();
                }
            }
        });

        compilations.forEach((method, compilation) -> compilationsByStart.put(method.getCodeAddressOffset(), compilation));

        for (int i = 0; i < sortedMethodOffsets.size() - 1; ++i) {
            int startOffset = sortedMethodOffsets.get(i);
            int endOffset = sortedMethodOffsets.get(i + 1);
            CompilationResult compilationResult = compilationsByStart.get(startOffset);
            assert startOffset + compilationResult.getTargetCodeSize() == endOffset : compilationResult.getName();
        }

        firstMethod = (HostedMethod) getFirstCompilation().getMethods()[0];
    }

    @Override
    public void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile) {
        Element rodataSection = objectFile.elementForName(SectionName.RODATA.getFormatDependentName(objectFile.getFormat()));
        Element dataSection = objectFile.elementForName(SectionName.DATA.getFormatDependentName(objectFile.getFormat()));
        for (CompilationResult result : getCompilations().values()) {
            for (DataPatch dataPatch : result.getDataPatches()) {
                if (dataPatch.reference instanceof CGlobalDataReference) {
                    CGlobalDataReference reference = (CGlobalDataReference) dataPatch.reference;

                    if (reference.getDataInfo().isSymbolReference()) {
                        objectFile.createUndefinedSymbol(reference.getDataInfo().getData().symbolName, 0, true);
                    }

                    int offset = reference.getDataInfo().getOffset();

                    String symbolName = (String) dataPatch.note;
                    if (objectFile.getOrCreateSymbolTable().getSymbol(symbolName) == null) {
                        objectFile.createDefinedSymbol(symbolName, dataSection, offset + RWDATA_CGLOBALS_PARTITION_OFFSET, 0, false, true);
                    }
                } else if (dataPatch.reference instanceof DataSectionReference) {
                    DataSectionReference reference = (DataSectionReference) dataPatch.reference;

                    int offset = reference.getOffset();

                    String symbolName = (String) dataPatch.note;
                    if (objectFile.getOrCreateSymbolTable().getSymbol(symbolName) == null) {
                        objectFile.createDefinedSymbol(symbolName, rodataSection, offset, 0, false, true);
                    }
                }
            }
        }
    }

    @Override
    public void writeCode(RelocatableBuffer buffer) {
        /* Do nothing, code is written at link stage */
    }

    @SuppressWarnings("try")
    private Path writeBitcode(DebugContext debug, Path basePath, String imageName) {
        List<String> paths;
        try (StopTimer t = new Timer(imageName, "(bitcode)").start()) {
            AtomicInteger num = new AtomicInteger(-1);
            paths = getCompilations().values().parallelStream().map(compilationResult -> {
                int id = num.incrementAndGet();
                String bitcodePath = basePath.resolve("llvm_" + id + ".bc").toString();

                try (FileOutputStream fos = new FileOutputStream(bitcodePath)) {
                    fos.write(compilationResult.getTargetCode());
                } catch (Exception e) {
                    throw new GraalError(e);
                }

                return bitcodePath;
            }).collect(Collectors.toList());
        }

        /* Compile LLVM */
        Path linkedBitcodePath = basePath.resolve("llvm.bc");
        try (StopTimer t = new Timer(imageName, "(link)").start()) {
            int maxThreads = NativeImageOptions.getMaximumNumberOfConcurrentThreads(ImageSingletons.lookup(HostedOptionValues.class));
            int numBatches = Math.max(maxThreads, paths.size() / BATCH_SIZE + ((paths.size() % BATCH_SIZE == 0) ? 0 : 1));
            int batchSize = paths.size() / numBatches + ((paths.size() % numBatches == 0) ? 0 : 1);
            List<List<String>> batchInputLists = IntStream.range(0, numBatches).mapToObj(i -> paths.stream()
                            .skip(i * batchSize)
                            .limit(batchSize)
                            .collect(Collectors.toList())).collect(Collectors.toList());

            AtomicInteger batchNum = new AtomicInteger(-1);
            List<String> batchPaths = batchInputLists.parallelStream()
                            .filter(inputList -> !inputList.isEmpty())
                            .map(batchInputs -> {
                                String batchOutputPath = basePath.resolve("llvm_batch" + batchNum.incrementAndGet() + ".bc").toString();

                                llvmLink(debug, batchOutputPath, batchInputs);

                                return batchOutputPath;
                            }).collect(Collectors.toList());

            llvmLink(debug, linkedBitcodePath.toString(), batchPaths);
        }

        Path optimizedBitcodePath = basePath.resolve("llvm_opt.bc");
        try (StopTimer t = new Timer(imageName, "(gc)").start()) {
            llvmOptimize(debug, optimizedBitcodePath.toString(), linkedBitcodePath.toString());
        }

        Path outputPath = basePath.resolve("llvm.o");
        try (StopTimer t = new Timer(imageName, "(llvm)").start()) {
            llvmCompile(debug, outputPath.toString(), optimizedBitcodePath.toString());
        }

        return outputPath;
    }

    private void llvmOptimize(DebugContext debug, String outputPath, String inputPath) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("opt");
            /*
             * Mem2reg has to be run before rewriting statepoints as it promotes allocas, which are
             * not supported for statepoints.
             */
            if (Platform.AMD64.class.isInstance(targetPlatform)) {
                cmd.add("-mem2reg");
                cmd.add("-rewrite-statepoints-for-gc");
                cmd.add("-always-inline");
            }
            cmd.add("-o");
            cmd.add(outputPath);
            cmd.add(inputPath);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("LLVM optimization failed for " + inputPath + ": " + status);
            }
        } catch (IOException | InterruptedException e) {
            throw new GraalError(e);
        }
    }

    private void llvmCompile(DebugContext debug, String outputPath, String inputPath) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("llc");
            cmd.add("-relocation-model=pic");

            /* X86 call frame optimization causes variable sized stack frames */
            if (Platform.AMD64.class.isInstance(targetPlatform)) {
                cmd.add("-no-x86-call-frame-opt");
            }
            if (Platform.AArch64.class.isInstance(targetPlatform)) {
                cmd.add("-march=arm64");
            }
            cmd.add("-O2");
            cmd.add("-filetype=obj");
            cmd.add("-o");
            cmd.add(outputPath);
            cmd.add(inputPath);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("LLVM compilation failed for " + inputPath + ": " + status);
            }
        } catch (IOException | InterruptedException e) {
            throw new GraalError(e);
        }
    }

    private static void llvmLink(DebugContext debug, String outputPath, List<String> inputPaths) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("llvm-link");
            cmd.add("-v");
            cmd.add("-o");
            cmd.add(outputPath);
            cmd.addAll(inputPaths);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("LLVM linking failed into " + outputPath + ": " + status);
            }
        } catch (IOException | InterruptedException e) {
            throw new GraalError(e);
        }
    }

    @Override
    public NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
        return new NativeTextSectionImpl(buffer, objectFile, codeCache) {
            @Override
            protected void defineMethodSymbol(String name, Element section, HostedMethod method, CompilationResult result) {
                objectFile.createUndefinedSymbol(name, 0, true);
            }
        };
    }

    @Override
    public String[] getCCInputFiles(Path tempDirectory, String imageName) {
        String relocatableFileName = tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix()).toString();
        try {
            Path src = Paths.get(bitcodeFileName);
            Path parent = Paths.get(relocatableFileName).getParent();
            if (parent != null) {
                Path dst = parent.resolve(src.getFileName());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new GraalError("Error copying " + bitcodeFileName + ": " + e);
        }
        return new String[]{relocatableFileName, bitcodeFileName};
    }
}
