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
import java.util.function.BiFunction;
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
import org.graalvm.compiler.core.llvm.LLVMUtils;
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
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;

@Platforms(Platform.HOSTED_ONLY.class)
public class LLVMNativeImageCodeCache extends NativeImageCodeCache {
    private static final String SYMBOL_PREFIX = (ObjectFile.getNativeFormat() == ObjectFile.Format.MACH_O) ? "_" : "";

    private HostedMethod[] methodIndex;
    private final Path basePath;
    private final FileWriter stackMapDump;
    private int batchSize;
    private Map<String, Integer> textSymbolOffsets = new HashMap<>();
    private Map<Integer, String> offsetToSymbolMap = new TreeMap<>();

    public LLVMNativeImageCodeCache(Map<HostedMethod, CompilationResult> compilations, NativeImageHeap imageHeap, Platform targetPlatform) {
        super(compilations, imageHeap, targetPlatform);

        try {
            basePath = Files.createTempDirectory("native-image-llvm");
            if (LLVMOptions.DumpLLVMStackMap.hasBeenSet()) {
                stackMapDump = new FileWriter(LLVMOptions.DumpLLVMStackMap.getValue());
            } else {
                stackMapDump = null;
            }
        } catch (IOException e) {
            throw new GraalError(e);
        }
    }

    @Override
    public int getCodeCacheSize() {
        return 0;
    }

    @Override
    @SuppressWarnings("try")
    public void layoutMethods(DebugContext debug, String imageName) {
        try (Indent indent = debug.logAndIndent("layout methods")) {
            try (StopTimer t = new Timer(imageName, "(bitcode)").start()) {
                writeBitcode();
            }
            int numBatches;
            try (StopTimer t = new Timer(imageName, "(prelink)").start()) {
                numBatches = createBitcodeBatches(debug);
            }
            try (StopTimer t = new Timer(imageName, "(llvm)").start()) {
                compileBitcodeBatches(debug, numBatches);
            }
            try (StopTimer t = new Timer(imageName, "(postlink)").start()) {
                linkCompiledBatches(debug, numBatches);
            }
        } catch (IOException e) {
            throw new GraalError(e);
        }
    }

    private static <T> T readSection(Path path, SectionName sectionName, BiFunction<LLVMSectionIteratorRef, LLVMObjectFileRef, T> callback) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new GraalError(e);
        }

        LLVMMemoryBufferRef buffer = LLVM.LLVMCreateMemoryBufferWithMemoryRangeCopy(new BytePointer(bytes), bytes.length, new BytePointer(""));
        LLVMObjectFileRef objectFile = LLVM.LLVMCreateObjectFile(buffer);

        LLVMSectionIteratorRef sectionIterator;
        T result = null;
        for (sectionIterator = LLVM.LLVMGetSections(objectFile); LLVM.LLVMIsSectionIteratorAtEnd(objectFile, sectionIterator) == FALSE; LLVM.LLVMMoveToNextSection(sectionIterator)) {
            BytePointer sectionNamePointer = LLVM.LLVMGetSectionName(sectionIterator);
            String currentSectionName = (sectionNamePointer != null) ? sectionNamePointer.getString() : "";
            if (currentSectionName.startsWith(sectionName.getFormatDependentName(ObjectFile.getNativeFormat()))) {
                result = callback.apply(sectionIterator, objectFile);
            }
        }

        LLVM.LLVMDisposeSectionIterator(sectionIterator);

        return result;
    }

    private Long readTextSection(LLVMSectionIteratorRef sectionIterator, LLVMObjectFileRef objectFile) {
        long codeSize = LLVM.LLVMGetSectionSize(sectionIterator);
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

        return codeSize;
    }

    @SuppressWarnings("unused")
    private LLVMStackMapInfo readStackMapSection(LLVMSectionIteratorRef sectionIterator, LLVMObjectFileRef objectFile) {
        Pointer stackMap = LLVM.LLVMGetSectionContents(sectionIterator).limit(LLVM.LLVMGetSectionSize(sectionIterator));
        return new LLVMStackMapInfo(stackMap.asByteBuffer());
    }

    private void storeMethodOffsets(long codeSize) throws IOException {
        List<Integer> sortedMethodOffsets = textSymbolOffsets.values().stream().distinct().sorted().collect(Collectors.toList());

        /*
         * Functions added by the LLVM backend have to be removed before computing function offsets,
         * because as they are not linked to a function known to Native Image, keeping them would
         * create gaps in the CodeInfoTable. Removing these offsets includes them as part of the
         * previously defined function instead. Stack walking will never see an address belonging to
         * one of these LLVM functions, as these are executing in native mode, so this will not
         * cause incorrect queries at runtime.
         */
        textSymbolOffsets.forEach((symbol, offset) -> {
            if (symbol.startsWith(SYMBOL_PREFIX + LLVMUtils.JNI_WRAPPER_PREFIX)) {
                sortedMethodOffsets.remove(offset);
            }
        });

        sortedMethodOffsets.add(NumUtil.safeToInt(codeSize));

        compilations.entrySet().parallelStream().forEach(entry -> {
            HostedMethod method = entry.getKey();
            String methodSymbolName = SYMBOL_PREFIX + SubstrateUtil.uniqueShortName(method);
            int offset = textSymbolOffsets.get(methodSymbolName);
            int nextFunctionStartOffset = sortedMethodOffsets.get(sortedMethodOffsets.indexOf(offset) + 1);
            int functionSize = nextFunctionStartOffset - offset;

            CompilationResult compilation = entry.getValue();
            compilation.setTargetCode(null, functionSize);
            method.setCodeAddressOffset(offset);
        });

        compilations.forEach((method, compilation) -> compilationsByStart.put(method.getCodeAddressOffset(), compilation));

        if (stackMapDump != null) {
            stackMapDump.write("Offsets\n=======\n");
        }
        for (int i = 0; i < sortedMethodOffsets.size() - 1; ++i) {
            int startOffset = sortedMethodOffsets.get(i);
            int endOffset = sortedMethodOffsets.get(i + 1);
            CompilationResult compilationResult = compilationsByStart.get(startOffset);
            assert startOffset + compilationResult.getTargetCodeSize() == endOffset : compilationResult.getName();

            if (stackMapDump != null) {
                String methodName = offsetToSymbolMap.get(startOffset);
                stackMapDump.write("[" + startOffset + "] " + methodName + " (" + compilationResult.getTargetCodeSize() + ")\n");
            }
        }

        HostedMethod firstMethod = (HostedMethod) getFirstCompilation().getMethods()[0];
        buildRuntimeMetadata(MethodPointer.factory(firstMethod), WordFactory.signed(codeSize));
    }

    private void readStackMap(LLVMStackMapInfo info, int batchId) {
        IntStream.range(getBatchStart(batchId), getBatchEnd(batchId)).forEach(id -> {
            HostedMethod method = methodIndex[id];
            String methodSymbolName = SYMBOL_PREFIX + SubstrateUtil.uniqueShortName(method);

            CompilationResult compilation = compilations.get(method);
            long startPatchpointID = compilation.getInfopoints().stream().filter(ip -> ip.reason == InfopointReason.METHOD_START).findFirst()
                            .orElseThrow(() -> new GraalError("no method start infopoint: " + methodSymbolName)).pcOffset;
            int totalFrameSize = NumUtil.safeToInt(info.getFunctionStackSize(startPatchpointID) + FrameAccess.returnAddressSize());
            compilation.setTotalFrameSize(totalFrameSize);

            StringBuilder patchpointsDump = null;
            if (stackMapDump != null) {
                patchpointsDump = new StringBuilder();
                patchpointsDump.append(methodSymbolName);
                patchpointsDump.append(" -> f");
                patchpointsDump.append(id);
                patchpointsDump.append(" (");
                patchpointsDump.append(totalFrameSize);
                patchpointsDump.append(")\n");
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

            if (stackMapDump != null) {
                try {
                    stackMapDump.write(patchpointsDump.toString());
                } catch (IOException e) {
                    throw new GraalError(e);
                }
            }
        });
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

    private void writeBitcode() {
        methodIndex = new HostedMethod[compilations.size()];
        AtomicInteger num = new AtomicInteger(-1);
        compilations.entrySet().parallelStream().forEach(entry -> {
            int id = num.incrementAndGet();
            methodIndex[id] = entry.getKey();

            try (FileOutputStream fos = new FileOutputStream(getBitcodePath(id).toString())) {
                fos.write(entry.getValue().getTargetCode());
            } catch (IOException e) {
                throw new GraalError(e);
            }
        });
    }

    private int createBitcodeBatches(DebugContext debug) {
        int maxThreads = NativeImageOptions.getMaximumNumberOfConcurrentThreads(ImageSingletons.lookup(HostedOptionValues.class));
        Integer parallelismLevel = LLVMOptions.LLVMBatchesPerThread.getValue();
        int numBatches;
        switch (parallelismLevel) {
            case -1:
                numBatches = methodIndex.length;
                break;
            case 0:
                numBatches = 1;
                break;
            default:
                numBatches = maxThreads * parallelismLevel;
        }

        batchSize = methodIndex.length / numBatches + ((methodIndex.length % numBatches == 0) ? 0 : 1);

        if (parallelismLevel != -1) {
            /* Avoid empty batches with small batch sizes */
            numBatches -= (numBatches * batchSize - methodIndex.length) / batchSize;

            IntStream.range(0, numBatches).parallel()
                            .forEach(batchId -> {
                                List<String> batchInputs = IntStream.range(getBatchStart(batchId), getBatchEnd(batchId)).mapToObj(this::getBitcodeFilename)
                                                .collect(Collectors.toList());
                                llvmLink(debug, getBatchBitcodeFilename(batchId), batchInputs);
                            });
        }

        return numBatches;
    }

    private void compileBitcodeBatches(DebugContext debug, int numBatches) throws IOException {
        if (stackMapDump != null) {
            stackMapDump.write("\nPatchpoints\n===========\n");
        }

        IntStream.range(0, numBatches).parallel().forEach(batchId -> {
            llvmOptimize(debug, getBatchOptimizedFilename(batchId), getBatchBitcodeFilename(batchId));
            llvmCompile(debug, getBatchCompiledFilename(batchId), getBatchOptimizedFilename(batchId));

            LLVMStackMapInfo stackMap = readSection(getBatchCompiledPath(batchId), SectionName.LLVM_STACKMAPS, this::readStackMapSection);
            readStackMap(stackMap, batchId);
        });
    }

    private void linkCompiledBatches(DebugContext debug, int numBatches) throws IOException {
        List<String> compiledBatches = IntStream.range(0, numBatches).mapToObj(this::getBatchCompiledFilename).collect(Collectors.toList());
        nativeLink(debug, getLinkedFilename(), compiledBatches);

        long codeSize = readSection(getLinkedPath(), SectionName.TEXT, this::readTextSection);
        storeMethodOffsets(codeSize);
    }

    private void llvmOptimize(DebugContext debug, String outputPath, String inputPath) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("opt");
            /*
             * Mem2reg has to be run before rewriting statepoints as it promotes allocas, which are
             * not supported for statepoints.
             */
            cmd.add("-mem2reg");
            cmd.add("-rewrite-statepoints-for-gc");
            cmd.add("-always-inline");

            cmd.add("-o");
            cmd.add(outputPath);
            cmd.add(inputPath);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("LLVM optimization failed for " + getFunctionName(inputPath) + ": " + status);
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
            if (targetPlatform instanceof Platform.AMD64) {
                cmd.add("-no-x86-call-frame-opt");
            }
            cmd.add("-O2");
            cmd.add("-filetype=obj");
            cmd.add("-o");
            cmd.add(outputPath);
            cmd.add(inputPath);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("LLVM compilation failed for " + getFunctionName(inputPath) + ": " + status);
            }
        } catch (IOException | InterruptedException e) {
            throw new GraalError(e);
        }
    }

    private void llvmLink(DebugContext debug, String outputPath, List<String> inputPaths) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("llvm-link");
            cmd.add("-v");
            cmd.add("-o");
            cmd.add(outputPath);
            cmd.addAll(inputPaths);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("LLVM linking failed into " + getFunctionName(outputPath) + ": " + status);
            }
        } catch (IOException | InterruptedException e) {
            throw new GraalError(e);
        }
    }

    private void nativeLink(DebugContext debug, String outputPath, List<String> inputPaths) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("ld");
            cmd.add("-r");
            cmd.add("-o");
            cmd.add(outputPath);
            cmd.addAll(inputPaths);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            OutputStream output = new ByteArrayOutputStream();
            FileUtils.drainInputStream(p.getInputStream(), output);

            int status = p.waitFor();
            if (status != 0) {
                debug.log("%s", output.toString());
                throw new GraalError("Native linking failed into " + getFunctionName(outputPath) + ": " + status);
            }
        } catch (IOException | InterruptedException e) {
            throw new GraalError(e);
        }
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
                    function = "batch " + id + " (f" + getBatchStart(id) + "-f" + getBatchEnd(id) + "). Use -H:LLVMBatchesPerThread=-1 to compile each method individually.";
                    break;
                default:
                    throw shouldNotReachHere();
            }
        }
        return function + " (" + basePath.resolve(fileName).toString() + ")";
    }

    @Override
    public NativeTextSectionImpl getTextSectionImpl(RelocatableBuffer buffer, ObjectFile objectFile, NativeImageCodeCache codeCache) {
        return new NativeTextSectionImpl(buffer, objectFile, codeCache) {
            @Override
            protected void defineMethodSymbol(String name, boolean global, Element section, HostedMethod method, CompilationResult result) {
                objectFile.createUndefinedSymbol(name, 0, true);
            }
        };
    }

    @Override
    public String[] getCCInputFiles(Path tempDirectory, String imageName) {
        String bitcodeFileName = getLinkedPath().toString();
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
