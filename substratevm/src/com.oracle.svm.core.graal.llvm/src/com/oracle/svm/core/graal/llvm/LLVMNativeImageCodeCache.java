/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.llvmCleanupStackMaps;
import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.llvmCompile;
import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.llvmLink;
import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.llvmOptimize;
import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.nativeLink;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;
import static com.oracle.svm.hosted.image.NativeImage.RWDATA_CGLOBALS_PARTITION_OFFSET;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.collections.Pair;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.c.CGlobalDataImpl;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.BatchExecutor;
import com.oracle.svm.core.graal.llvm.objectfile.LLVMObjectFile;
import com.oracle.svm.core.graal.llvm.util.LLVMObjectFileReader;
import com.oracle.svm.core.graal.llvm.util.LLVMObjectFileReader.LLVMTextSectionInfo;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMStackMapInfo;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.image.NativeImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;

@Platforms(Platform.HOSTED_ONLY.class)
public class LLVMNativeImageCodeCache extends NativeImageCodeCache {
    private HostedMethod[] methodIndex;
    private final Path basePath;
    private int batchSize;
    private final LLVMObjectFileReader objectFileReader;
    private final List<ObjectFile.Symbol> globalSymbols = new ArrayList<>();
    private final StackMapDumper stackMapDumper;

    LLVMNativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform, Path tempDir) {
        super(compilations, imageHeap, targetPlatform);

        try {
            basePath = tempDir.resolve("llvm");
            Files.createDirectory(basePath);
        } catch (IOException e) {
            throw new GraalError(e);
        }

        this.stackMapDumper = getStackMapDumper(LLVMOptions.DumpLLVMStackMap.hasBeenSet());
        this.objectFileReader = new LLVMObjectFileReader(stackMapDumper);
    }

    @Override
    public int getCodeCacheSize() {
        return 0;
    }

    @Override
    public int codeSizeFor(HostedMethod method) {
        return compilationResultFor(method).getTargetCodeSize();
    }

    @Override
    @SuppressWarnings({"unused", "try"})
    public void layoutMethods(DebugContext debug, BigBang bb) {
        try (Indent indent = debug.logAndIndent("layout methods")) {
            BatchExecutor executor = new BatchExecutor(debug, bb);
            try (StopTimer t = TimerCollection.createTimerAndStart("(bitcode)")) {
                writeBitcode(executor);
            }
            int numBatches;
            try (StopTimer t = TimerCollection.createTimerAndStart("(prelink)")) {
                numBatches = createBitcodeBatches(executor, debug);
            }
            try (StopTimer t = TimerCollection.createTimerAndStart("(llvm)")) {
                compileBitcodeBatches(executor, debug, numBatches);
            }
            try (StopTimer t = TimerCollection.createTimerAndStart("(postlink)")) {
                linkCompiledBatches(debug, executor, numBatches);
            }
        }
    }

    private void writeBitcode(BatchExecutor executor) {
        methodIndex = new HostedMethod[getOrderedCompilations().size()];
        AtomicInteger num = new AtomicInteger(-1);
        executor.forEach(getOrderedCompilations(), pair -> (debugContext) -> {
            int id = num.incrementAndGet();
            methodIndex[id] = pair.getLeft();

            try (FileOutputStream fos = new FileOutputStream(getBitcodePath(id).toString())) {
                fos.write(pair.getRight().getTargetCode());
            } catch (IOException e) {
                throw new GraalError(e);
            }
        });
    }

    private int createBitcodeBatches(BatchExecutor executor, DebugContext debug) {
        batchSize = LLVMOptions.LLVMMaxFunctionsPerBatch.getValue();
        int numThreads = NativeImageOptions.getActualNumberOfThreads();
        int idealSize = NumUtil.divideAndRoundUp(methodIndex.length, numThreads);
        if (idealSize < batchSize) {
            batchSize = idealSize;
        }

        if (batchSize == 0) {
            batchSize = methodIndex.length;
        }
        int numBatches = NumUtil.divideAndRoundUp(methodIndex.length, batchSize);
        if (batchSize > 1) {
            /* Avoid empty batches with small batch sizes */
            numBatches -= (numBatches * batchSize - methodIndex.length) / batchSize;

            executor.forEach(numBatches, batchId -> (debugContext) -> {
                List<String> batchInputs = IntStream.range(getBatchStart(batchId), getBatchEnd(batchId)).mapToObj(this::getBitcodeFilename)
                                .collect(Collectors.toList());
                llvmLink(debug, getBatchBitcodeFilename(batchId), batchInputs, basePath, this::getFunctionName);
            });
        }

        return numBatches;
    }

    private void compileBitcodeBatches(BatchExecutor executor, DebugContext debug, int numBatches) {
        stackMapDumper.startDumpingFunctions();

        executor.forEach(numBatches, batchId -> (debugContext) -> {
            llvmOptimize(debug, getBatchOptimizedFilename(batchId), getBatchBitcodeFilename(batchId), basePath, this::getFunctionName);
            llvmCompile(debug, getBatchCompiledFilename(batchId), getBatchOptimizedFilename(batchId), basePath, this::getFunctionName);

            LLVMStackMapInfo stackMap = objectFileReader.parseStackMap(getBatchCompiledPath(batchId));
            IntStream.range(getBatchStart(batchId), getBatchEnd(batchId)).forEach(id -> objectFileReader.readStackMap(stackMap, compilationResultFor(methodIndex[id]), methodIndex[id], id));
        });
    }

    private void linkCompiledBatches(DebugContext debug, BatchExecutor executor, int numBatches) {
        List<String> compiledBatches = IntStream.range(0, numBatches).mapToObj(this::getBatchCompiledFilename).collect(Collectors.toList());
        nativeLink(debug, getLinkedFilename(), compiledBatches, basePath, this::getFunctionName);

        LLVMTextSectionInfo textSectionInfo = objectFileReader.parseCode(getLinkedPath());

        executor.forEach(getOrderedCompilations(), pair -> (debugContext) -> {
            HostedMethod method = pair.getLeft();
            int offset = textSectionInfo.getOffset(method.getUniqueShortName());
            int nextFunctionStartOffset = textSectionInfo.getNextOffset(offset);
            int functionSize = nextFunctionStartOffset - offset;

            CompilationResult compilation = pair.getRight();
            compilation.setTargetCode(null, functionSize);
            method.setCodeAddressOffset(offset);
        });

        getOrderedCompilations().sort(Comparator.comparingInt(o -> o.getLeft().getCodeAddressOffset()));
        stackMapDumper.dumpOffsets(textSectionInfo);
        stackMapDumper.close();

        llvmCleanupStackMaps(debug, getLinkedFilename(), basePath);
        long codeAreaSize = textSectionInfo.getCodeSize();
        assert codeAreaSize <= Integer.MAX_VALUE;
        setCodeAreaSize((int) textSectionInfo.getCodeSize());
    }

    private Path getBitcodePath(int id) {
        return basePath.resolve(getBitcodeFilename(id));
    }

    private String getBitcodeFilename(int id) {
        return "f" + id + ".bc";
    }

    private String getBatchBitcodeFilename(int id) {
        return ((batchSize == 1) ? "f" : "b") + id + ".bc";
    }

    private String getBatchOptimizedFilename(int id) {
        return ((batchSize == 1) ? "f" : "b") + id + "o.bc";
    }

    private Path getBatchCompiledPath(int id) {
        return basePath.resolve(getBatchCompiledFilename(id));
    }

    private String getBatchCompiledFilename(int id) {
        return ((batchSize == 1) ? "f" : "b") + id + ".o";
    }

    private Path getLinkedPath() {
        return basePath.resolve(getLinkedFilename());
    }

    private static String getLinkedFilename() {
        return "llvm.o";
    }

    private int getBatchStart(int id) {
        return id * batchSize;
    }

    private int getBatchEnd(int id) {
        return Math.min((id + 1) * batchSize, methodIndex.length);
    }

    private String getFunctionName(String fileName) {
        String function;
        if (fileName.equals("llvm.o")) {
            function = "the final object file";
        } else {
            char type = fileName.charAt(0);
            String idString = fileName.substring(1, fileName.indexOf('.'));
            if (idString.charAt(idString.length() - 1) == 'o') {
                idString = idString.substring(0, idString.length() - 1);
            }
            int id = Integer.parseInt(idString);

            switch (type) {
                case 'f':
                    function = methodIndex[id].getQualifiedName();
                    break;
                case 'b':
                    function = "batch " + id + " (f" + getBatchStart(id) + "-f" + getBatchEnd(id) + "). Use -H:LLVMMaxFunctionsPerBatch=1 to compile each method individually.";
                    break;
                default:
                    throw shouldNotReachHereUnexpectedInput(type);
            }
        }
        return function + " (" + basePath.resolve(fileName).toString() + ")";
    }

    @Override
    public void patchMethods(DebugContext debug, RelocatableBuffer relocs, ObjectFile objectFile) {
        Element rodataSection = objectFile.elementForName(SectionName.RODATA.getFormatDependentName(objectFile.getFormat()));
        Element dataSection = objectFile.elementForName(SectionName.DATA.getFormatDependentName(objectFile.getFormat()));
        for (Pair<HostedMethod, CompilationResult> pair : getOrderedCompilations()) {
            CompilationResult result = pair.getRight();
            for (DataPatch dataPatch : result.getDataPatches()) {
                if (dataPatch.reference instanceof CGlobalDataReference) {
                    CGlobalDataInfo info = ((CGlobalDataReference) dataPatch.reference).getDataInfo();
                    CGlobalDataImpl<?> data = info.getData();
                    if (info.isSymbolReference() && objectFile.getOrCreateSymbolTable().getSymbol(data.symbolName) == null) {
                        objectFile.createUndefinedSymbol(data.symbolName, 0, true);
                    }

                    String symbolName = (String) dataPatch.note;
                    if (data.symbolName == null && objectFile.getOrCreateSymbolTable().getSymbol(symbolName) == null) {
                        objectFile.createDefinedSymbol(symbolName, dataSection, info.getOffset() + RWDATA_CGLOBALS_PARTITION_OFFSET, 0, false, true);
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
    public NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
        return new NativeTextSectionImpl(buffer, objectFile, codeCache) {
            @Override
            protected void defineMethodSymbol(String name, boolean global, Element section, HostedMethod method, CompilationResult result) {
                ObjectFile.Symbol symbol = objectFile.createUndefinedSymbol(name, 0, true);
                if (global) {
                    globalSymbols.add(symbol);
                }
            }
        };
    }

    @Override
    public void writeCode(RelocatableBuffer buffer) {
        /* Do nothing, code is written at link stage */
    }

    @Override
    public Path[] getCCInputFiles(Path tempDirectory, String imageName) {
        Path[] allInputFiles;
        if (LLVMOptions.UseLLVMDataSection.getValue()) {
            allInputFiles = new Path[2];
            allInputFiles[0] = basePath.resolve(LLVMObjectFile.getLinkedFilename());
        } else {
            Path[] nativeImageFiles = super.getCCInputFiles(tempDirectory, imageName);
            allInputFiles = Arrays.copyOf(nativeImageFiles, nativeImageFiles.length + 1);
        }
        Path bitcodeFileName = getLinkedPath();
        allInputFiles[allInputFiles.length - 1] = bitcodeFileName;
        return allInputFiles;
    }

    @Override
    public List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile) {
        return globalSymbols;
    }

    private StackMapDumper getStackMapDumper(boolean enable) {
        if (enable) {
            return new EnabledStackMapDumper();
        } else {
            return new DisabledStackMapDumper();
        }
    }

    public interface StackMapDumper {
        void dumpOffsets(LLVMTextSectionInfo textSectionInfo);

        void startDumpingFunctions();

        void startDumpingFunction(String methodSymbolName, int id, int totalFrameSize);

        void dumpCallSite(Call call, int actualPcOffset, SubstrateReferenceMap referenceMap);

        void endDumpingFunction();

        void close();
    }

    private class EnabledStackMapDumper implements StackMapDumper {
        private final FileWriter stackMapDump;

        {
            try {
                stackMapDump = new FileWriter(LLVMOptions.DumpLLVMStackMap.getValue());
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }

        private ThreadLocal<StringBuilder> functionDump = new ThreadLocal<>();

        @Override
        public void dumpOffsets(LLVMTextSectionInfo textSectionInfo) {
            dump("\nOffsets\n=======\n");
            getOrderedCompilations().forEach((pair) -> {
                int startOffset = pair.getLeft().getCodeAddressOffset();
                CompilationResult compilationResult = pair.getRight();
                assert startOffset + compilationResult.getTargetCodeSize() == textSectionInfo.getNextOffset(startOffset) : compilationResult.getName();

                String methodName = textSectionInfo.getSymbol(startOffset);
                dump("[" + startOffset + "] " + methodName + " (" + compilationResult.getTargetCodeSize() + ")\n");
            });
        }

        @Override
        public void startDumpingFunctions() {
            dump("Patchpoints\n===========\n");
        }

        @Override
        public void startDumpingFunction(String methodSymbolName, int id, int totalFrameSize) {
            StringBuilder builder = new StringBuilder();
            builder.append(methodSymbolName);
            builder.append(" -> f");
            builder.append(id);
            builder.append(" (");
            builder.append(totalFrameSize);
            builder.append(")\n");
            functionDump.set(builder);
        }

        @Override
        public void dumpCallSite(Call call, int actualPcOffset, SubstrateReferenceMap referenceMap) {
            StringBuilder builder = functionDump.get();
            builder.append("  [");
            builder.append(actualPcOffset);
            builder.append("] -> ");
            builder.append(call.target != null ? ((HostedMethod) call.target).format("%H.%n") : "???");
            builder.append(" (");
            builder.append(call.pcOffset);
            builder.append(") ");
            referenceMap.dump(builder);
            builder.append("\n");
        }

        @Override
        public void endDumpingFunction() {
            dump(functionDump.get().toString());
        }

        @Override
        public void close() {
            try {
                stackMapDump.close();
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }

        private void dump(String str) {
            try {
                stackMapDump.write(str);
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }
    }

    private static class DisabledStackMapDumper implements StackMapDumper {
        @Override
        public void dumpOffsets(LLVMTextSectionInfo textSectionInfo) {
        }

        @Override
        public void startDumpingFunctions() {
        }

        @Override
        public void startDumpingFunction(String methodSymbolName, int id, int totalFrameSize) {
        }

        @Override
        public void dumpCallSite(Call call, int actualPcOffset, SubstrateReferenceMap referenceMap) {
        }

        @Override
        public void endDumpingFunction() {
        }

        @Override
        public void close() {
        }
    }
}
