/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.FALSE;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.TRUE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.llvm.LLVMGenerator;
import com.oracle.svm.core.graal.llvm.LLVMNativeImageCodeCache.StackMapDumper;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.BytePointer;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.Pointer;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMMemoryBufferRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMObjectFileRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMSectionIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMSymbolIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LLVMObjectFileReader {
    private static final String SYMBOL_PREFIX = (ObjectFile.getNativeFormat() == ObjectFile.Format.MACH_O) ? "_" : "";
    private final StackMapDumper stackMapDumper;

    public LLVMObjectFileReader(StackMapDumper stackMapDumper) {
        this.stackMapDumper = stackMapDumper;
    }

    @FunctionalInterface
    private interface SectionReader<Result> {
        Result apply(LLVMSectionIteratorRef sectionIterator);
    }

    @FunctionalInterface
    private interface SymbolReader<Symbol> {
        Symbol apply(LLVMSymbolIteratorRef symbolIterator, LLVMSectionIteratorRef sectionIterator);
    }

    private static class LLVMSectionInfo<Section, Symbol> {
        private Section sectionInfo;
        private List<Symbol> symbolInfo = new ArrayList<>();
    }

    private static <SectionInfo, SymbolInfo> LLVMSectionInfo<SectionInfo, SymbolInfo> readSection(Path path, SectionName sectionName, SectionReader<SectionInfo> sectionReader,
                    SymbolReader<SymbolInfo> symbolReader) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new GraalError(e);
        }

        LLVMMemoryBufferRef buffer = LLVM.LLVMCreateMemoryBufferWithMemoryRangeCopy(new BytePointer(bytes), bytes.length, new BytePointer(""));
        LLVMObjectFileRef objectFile = LLVM.LLVMCreateObjectFile(buffer);

        LLVMSectionIteratorRef sectionIterator;
        LLVMSectionInfo<SectionInfo, SymbolInfo> result = new LLVMSectionInfo<>();
        for (sectionIterator = LLVM.LLVMGetSections(objectFile); LLVM.LLVMIsSectionIteratorAtEnd(objectFile, sectionIterator) == FALSE; LLVM.LLVMMoveToNextSection(sectionIterator)) {
            BytePointer sectionNamePointer = LLVM.LLVMGetSectionName(sectionIterator);
            String currentSectionName = (sectionNamePointer != null) ? sectionNamePointer.getString() : "";
            if (currentSectionName.startsWith(sectionName.getFormatDependentName(ObjectFile.getNativeFormat()))) {
                result.sectionInfo = sectionReader.apply(sectionIterator);

                if (symbolReader != null) {
                    LLVMSymbolIteratorRef symbolIterator;
                    for (symbolIterator = LLVM.LLVMGetSymbols(objectFile); LLVM.LLVMIsSymbolIteratorAtEnd(objectFile, symbolIterator) == FALSE; LLVM.LLVMMoveToNextSymbol(symbolIterator)) {
                        if (LLVM.LLVMGetSectionContainsSymbol(sectionIterator, symbolIterator) == TRUE) {
                            result.symbolInfo.add(symbolReader.apply(symbolIterator, sectionIterator));
                        }
                    }
                    LLVM.LLVMDisposeSymbolIterator(symbolIterator);
                }
                break;
            }
        }

        LLVM.LLVMDisposeSectionIterator(sectionIterator);
        LLVM.LLVMDisposeObjectFile(objectFile);

        return result;
    }

    private static final class SymbolOffset {
        private final String symbol;
        private final int offset;

        private SymbolOffset(String symbol, int offset) {
            this.symbol = symbol;
            this.offset = offset;
        }
    }

    public LLVMTextSectionInfo parseCode(Path objectFile) {
        LLVMSectionInfo<Long, SymbolOffset> sectionInfo = readSection(objectFile, SectionName.TEXT, this::parseTextSection, this::handleTextSymbol);
        return new LLVMTextSectionInfo(sectionInfo);
    }

    private Long parseTextSection(LLVMSectionIteratorRef sectionIterator) {
        return LLVM.LLVMGetSectionSize(sectionIterator);
    }

    private SymbolOffset handleTextSymbol(LLVMSymbolIteratorRef symbolIterator, LLVMSectionIteratorRef sectionIterator) {
        long sectionAddress = LLVM.LLVMGetSectionAddress(sectionIterator);
        int offset = NumUtil.safeToInt(LLVM.LLVMGetSymbolAddress(symbolIterator) - sectionAddress);
        String symbolName = LLVM.LLVMGetSymbolName(symbolIterator).getString();
        return new SymbolOffset(symbolName, offset);
    }

    public LLVMStackMapInfo parseStackMap(Path objectFile) {
        LLVMSectionInfo<LLVMStackMapInfo, Object> sectionInfo = readSection(objectFile, SectionName.LLVM_STACKMAPS, this::readStackMapSection, null);
        return sectionInfo.sectionInfo;
    }

    private LLVMStackMapInfo readStackMapSection(LLVMSectionIteratorRef sectionIterator) {
        Pointer stackMap = LLVM.LLVMGetSectionContents(sectionIterator).limit(LLVM.LLVMGetSectionSize(sectionIterator));
        return new LLVMStackMapInfo(stackMap.asByteBuffer());
    }

    public void readStackMap(LLVMStackMapInfo info, CompilationResult compilation, ResolvedJavaMethod method, int id) {
        String methodSymbolName = SYMBOL_PREFIX + SubstrateUtil.uniqueShortName(method);

        long startPatchpointID = compilation.getInfopoints().stream().filter(ip -> ip.reason == InfopointReason.METHOD_START).findFirst()
                        .orElseThrow(() -> new GraalError("no method start infopoint: " + methodSymbolName)).pcOffset;
        int totalFrameSize = NumUtil.safeToInt(info.getFunctionStackSize(startPatchpointID) + LLVMTargetSpecific.get().getCallFrameSeparation());
        compilation.setTotalFrameSize(totalFrameSize);
        stackMapDumper.startDumpingFunction(methodSymbolName, id, totalFrameSize);

        List<Infopoint> newInfopoints = new ArrayList<>();
        for (Infopoint infopoint : compilation.getInfopoints()) {
            if (infopoint instanceof Call) {
                Call call = (Call) infopoint;

                /* Optimizations might have duplicated some calls. */
                for (int actualPcOffset : info.getPatchpointOffsets(call.pcOffset)) {
                    SubstrateReferenceMap referenceMap = new SubstrateReferenceMap();
                    info.forEachStatepointOffset(call.pcOffset, actualPcOffset, referenceMap::markReferenceAtOffset);
                    stackMapDumper.dumpCallSite(call, actualPcOffset, referenceMap);
                    newInfopoints.add(new Call(call.target, actualPcOffset, call.size, call.direct, copyWithReferenceMap(call.debugInfo, referenceMap)));
                }
            }
        }
        stackMapDumper.endDumpingFunction();

        compilation.clearInfopoints();
        newInfopoints.forEach(compilation::addInfopoint);
    }

    private static DebugInfo copyWithReferenceMap(DebugInfo debugInfo, ReferenceMap referenceMap) {
        DebugInfo newInfo = new DebugInfo(debugInfo.getBytecodePosition(), debugInfo.getVirtualObjectMapping());
        newInfo.setCalleeSaveInfo(debugInfo.getCalleeSaveInfo());
        newInfo.setReferenceMap(referenceMap);
        return newInfo;
    }

    public static final class LLVMTextSectionInfo {
        private final long codeSize;
        private final Map<Integer, String> offsetToSymbol = new TreeMap<>();
        private final Map<String, Integer> symbolToOffset = new HashMap<>();
        private final List<Integer> sortedMethodOffsets;

        private LLVMTextSectionInfo(LLVMSectionInfo<Long, SymbolOffset> sectionInfo) {
            this.codeSize = sectionInfo.sectionInfo;
            for (SymbolOffset symbolOffset : sectionInfo.symbolInfo) {
                offsetToSymbol.put(symbolOffset.offset, symbolOffset.symbol);
                symbolToOffset.put(symbolOffset.symbol, symbolOffset.offset);
            }
            this.sortedMethodOffsets = computeSortedMethodOffsets();
        }

        public long getCodeSize() {
            return codeSize;
        }

        public String getSymbol(int offset) {
            return offsetToSymbol.get(offset);
        }

        public int getOffset(String methodName) {
            return symbolToOffset.get(SYMBOL_PREFIX + methodName);
        }

        public int getNextOffset(int offset) {
            return sortedMethodOffsets.get(sortedMethodOffsets.indexOf(offset) + 1);
        }

        @FunctionalInterface
        public interface OffsetRangeConsumer {
            void apply(int start, int end);
        }

        public void forEachOffsetRange(OffsetRangeConsumer consumer) {
            for (int i = 0; i < sortedMethodOffsets.size() - 1; ++i) {
                consumer.apply(sortedMethodOffsets.get(i), sortedMethodOffsets.get(i + 1));
            }
        }

        private List<Integer> computeSortedMethodOffsets() {
            List<Integer> sortedOffsets = offsetToSymbol.keySet().stream().distinct().sorted().collect(Collectors.toList());

            /*
             * Functions added by the LLVM backend have to be removed before computing function
             * offsets, because as they are not linked to a function known to Native Image, keeping
             * them would create gaps in the CodeInfoTable. Removing these offsets includes them as
             * part of the previously defined function instead. Stack walking will never see an
             * address belonging to one of these LLVM functions, as these are executing in native
             * mode, so this will not cause incorrect queries at runtime.
             */
            symbolToOffset.forEach((symbol, offset) -> {
                if (symbol.startsWith(SYMBOL_PREFIX + LLVMGenerator.JNI_WRAPPER_BASE_NAME)) {
                    sortedOffsets.remove(offset);
                }
            });

            sortedOffsets.add(NumUtil.safeToInt(codeSize));

            return sortedOffsets;
        }
    }
}
