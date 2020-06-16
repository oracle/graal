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

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.llvm.util.LLVMObjectFileReader;
import com.oracle.svm.core.graal.llvm.util.LLVMObjectFileReader.LLVMTextSectionInfo;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMStackMapInfo;
import com.oracle.svm.core.graal.llvm.util.LLVMTargetSpecific;
import com.oracle.svm.core.graal.llvm.util.LLVMToolchain;
import com.oracle.svm.core.graal.llvm.util.LLVMToolchain.RunFailureException;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.hosted.image.NativeBootImage.NativeTextSectionImpl;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.MethodPointer;

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
    @SuppressWarnings({"unused", "try"})
    public void layoutMethods(DebugContext debug, String imageName, BigBang bb, ForkJoinPool threadPool) {
        try (Indent indent = debug.logAndIndent("layout methods")) {
            BatchExecutor executor = new BatchExecutor(bb, threadPool);
            try (StopTimer t = new Timer(imageName, "(bitcode)").start()) {
                writeBitcode(executor);
            }
            int numBatches;
            try (StopTimer t = new Timer(imageName, "(prelink)").start()) {
                numBatches = createBitcodeBatches(executor, debug);
            }
            try (StopTimer t = new Timer(imageName, "(llvm)").start()) {
                compileBitcodeBatches(executor, debug, numBatches);
            }
            try (StopTimer t = new Timer(imageName, "(postlink)").start()) {
                linkCompiledBatches(executor, debug, numBatches);
            }
        }
    }

    private void writeBitcode(BatchExecutor executor) {
        methodIndex = new HostedMethod[compilations.size()];
        AtomicInteger num = new AtomicInteger(-1);
        executor.forEach(compilations.entrySet(), entry -> (debugContext) -> {
            int id = num.incrementAndGet();
            methodIndex[id] = entry.getKey();

            try (FileOutputStream fos = new FileOutputStream(getBitcodePath(id).toString())) {
                fos.write(entry.getValue().getTargetCode());
            } catch (IOException e) {
                throw new GraalError(e);
            }
        });
    }

    private int createBitcodeBatches(BatchExecutor executor, DebugContext debug) {
        batchSize = LLVMOptions.LLVMMaxFunctionsPerBatch.getValue();
        int numThreads = executor.executor.getExecutorService().getParallelism();
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
                llvmLink(debug, getBatchBitcodeFilename(batchId), batchInputs);
            });
        }

        return numBatches;
    }

    private void compileBitcodeBatches(BatchExecutor executor, DebugContext debug, int numBatches) {
        stackMapDumper.startDumpingFunctions();

        executor.forEach(numBatches, batchId -> (debugContext) -> {
            llvmOptimize(debug, getBatchOptimizedFilename(batchId), getBatchBitcodeFilename(batchId));
            llvmCompile(debug, getBatchCompiledFilename(batchId), getBatchOptimizedFilename(batchId));

            LLVMStackMapInfo stackMap = objectFileReader.parseStackMap(getBatchCompiledPath(batchId));
            IntStream.range(getBatchStart(batchId), getBatchEnd(batchId)).forEach(id -> objectFileReader.readStackMap(stackMap, compilations.get(methodIndex[id]), methodIndex[id], id));
        });
    }

    private void linkCompiledBatches(BatchExecutor executor, DebugContext debug, int numBatches) {
        List<String> compiledBatches = IntStream.range(0, numBatches).mapToObj(this::getBatchCompiledFilename).collect(Collectors.toList());
        nativeLink(debug, getLinkedFilename(), compiledBatches);

        LLVMTextSectionInfo textSectionInfo = objectFileReader.parseCode(getLinkedPath());

        executor.forEach(compilations.entrySet(), entry -> (debugContext) -> {
            HostedMethod method = entry.getKey();
            int offset = textSectionInfo.getOffset(SubstrateUtil.uniqueShortName(method));
            int nextFunctionStartOffset = textSectionInfo.getNextOffset(offset);
            int functionSize = nextFunctionStartOffset - offset;

            CompilationResult compilation = entry.getValue();
            compilation.setTargetCode(null, functionSize);
            method.setCodeAddressOffset(offset);
        });

        compilations.forEach((method, compilation) -> compilationsByStart.put(method.getCodeAddressOffset(), compilation));
        stackMapDumper.dumpOffsets(textSectionInfo);
        stackMapDumper.close();

        HostedMethod firstMethod = (HostedMethod) getFirstCompilation().getMethods()[0];
        buildRuntimeMetadata(MethodPointer.factory(firstMethod), WordFactory.signed(textSectionInfo.getCodeSize()));
    }

    private void llvmOptimize(DebugContext debug, String outputPath, String inputPath) {
        List<String> args = new ArrayList<>();
        if (LLVMOptions.BitcodeOptimizations.getValue()) {
            /*
             * This runs LLVM's bitcode optimizations in addition to the Graal optimizations.
             * Inlining has to be disabled in this case as the functions are already stored in the
             * image heap and inlining them would produce bogus runtime information for garbage
             * collection and exception handling.
             */
            args.add("-disable-inlining");
            args.add("-O2");
        } else {
            /*
             * Mem2reg has to be run before rewriting statepoints as it promotes allocas, which are
             * not supported for statepoints.
             */
            args.add("-mem2reg");
        }
        args.add("-rewrite-statepoints-for-gc");
        args.add("-always-inline");

        args.add("-o");
        args.add(outputPath);
        args.add(inputPath);

        try {
            LLVMToolchain.runLLVMCommand("opt", basePath, args);
        } catch (RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("LLVM optimization failed for " + getFunctionName(inputPath) + ": " + e.getStatus() + "\nCommand: opt " + String.join(" ", args));
        }
    }

    private void llvmCompile(DebugContext debug, String outputPath, String inputPath) {
        List<String> args = new ArrayList<>();
        args.add("-relocation-model=pic");
        /*
         * Makes sure that unreachable instructions get emitted into the machine code. This prevents
         * a situation where a call is the last instruction of a function, resulting in its return
         * address being located in the next function, which causes trouble with runtime information
         * emission.
         */
        args.add("--trap-unreachable");
        args.add("-march=" + LLVMTargetSpecific.get().getLLVMArchName());
        args.addAll(LLVMTargetSpecific.get().getLLCAdditionalOptions());
        args.add("-O" + SubstrateOptions.Optimize.getValue());
        args.add("-filetype=obj");
        args.add("-o");
        args.add(outputPath);
        args.add(inputPath);

        try {
            LLVMToolchain.runLLVMCommand("llc", basePath, args);
        } catch (RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("LLVM compilation failed for " + getFunctionName(inputPath) + ": " + e.getStatus() + "\nCommand: llc " + String.join(" ", args));
        }
    }

    private void llvmLink(DebugContext debug, String outputPath, List<String> inputPaths) {
        List<String> args = new ArrayList<>();
        args.add("-o");
        args.add(outputPath);
        args.addAll(inputPaths);

        try {
            LLVMToolchain.runLLVMCommand("llvm-link", basePath, args);
        } catch (RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("LLVM linking failed into " + getFunctionName(outputPath) + ": " + e.getStatus());
        }
    }

    private void nativeLink(DebugContext debug, String outputPath, List<String> inputPaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add((LLVMOptions.CustomLD.hasBeenSet()) ? LLVMOptions.CustomLD.getValue() : "ld");
        cmd.add("-r");
        cmd.add("-o");
        cmd.add(outputPath);
        cmd.addAll(inputPaths);

        try {
            LLVMToolchain.runCommand(basePath, cmd);
        } catch (RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("Native linking failed into " + getFunctionName(outputPath) + ": " + e.getStatus());
        }
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
                    throw shouldNotReachHere();
            }
        }
        return function + " (" + basePath.resolve(fileName).toString() + ")";
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
                    if (reference.getDataInfo().getData().symbolName == null && objectFile.getOrCreateSymbolTable().getSymbol(symbolName) == null) {
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
        Path[] nativeImageFiles = super.getCCInputFiles(tempDirectory, imageName);
        Path[] allInputFiles = Arrays.copyOf(nativeImageFiles, nativeImageFiles.length + 1);

        Path bitcodeFileName = getLinkedPath();
        allInputFiles[nativeImageFiles.length] = bitcodeFileName;
        return allInputFiles;
    }

    @Override
    public List<ObjectFile.Symbol> getSymbols(ObjectFile objectFile, boolean onlyGlobal) {
        return globalSymbols;
    }

    private static final class BatchExecutor {
        private CompletionExecutor executor;

        private BatchExecutor(BigBang bb, ForkJoinPool threadPool) {
            this.executor = new CompletionExecutor(bb, threadPool, bb.getHeartbeatCallback());
            executor.init();
        }

        private void forEach(int num, IntFunction<DebugContextRunnable> callback) {
            try {
                executor.start();
                for (int i = 0; i < num; ++i) {
                    executor.execute(callback.apply(i));
                }
                executor.complete();
                executor.init();
            } catch (InterruptedException e) {
                throw new GraalError(e);
            }
        }

        private <T> void forEach(Set<T> set, Function<T, DebugContextRunnable> callback) {
            try {
                executor.start();
                for (T elem : set) {
                    executor.execute(callback.apply(elem));
                }
                executor.complete();
                executor.init();
            } catch (InterruptedException e) {
                throw new GraalError(e);
            }
        }
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
            textSectionInfo.forEachOffsetRange((startOffset, endOffset) -> {
                CompilationResult compilationResult = compilationsByStart.get(startOffset);
                assert startOffset + compilationResult.getTargetCodeSize() == endOffset : compilationResult.getName();

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
