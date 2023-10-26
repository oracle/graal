/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.objectfile;

import static com.oracle.objectfile.ObjectFile.Format.LLVM;
import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.llvmCompile;
import static com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.nativeLink;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jdk.graal.compiler.debug.DebugContext;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SymbolTable;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.llvm.LLVMToolchainUtils.BatchExecutor;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;

/**
 * Represents an object file emitted using LLVM.
 * <p>
 * The content of the data sections is emitted as
 * <a href="https://llvm.org/docs/LangRef.html#global-variables"> LLVM global variables</a> using
 * <a href="https://www.llvm.org/docs/BitCodeFormat.html">LLVM bitcode</a>, the LLVM intermediate
 * representation (see {@link LLVMDataSectionPart}). LLVM creates a symbol for each global variable
 * using its name and the layout of the sections is automatically decided by LLVM. The relocations
 * can be represented using the global variables and simple
 * <a href="https://llvm.org/docs/LangRef.html#instruction-reference">LLVM bitcode instructions</a>.
 * The bitcode is then compiled to binary with llc.
 */
public class LLVMObjectFile extends ObjectFile {
    private ByteOrder byteOrder;
    private final LLVMIRBuilder builder;
    @SuppressWarnings("unused") private final LLVMHeader header;
    private final Path basePath;
    private final BigBang bb;

    private final List<LLVMDataSectionPart> dataSectionParts = new ArrayList<>();

    public static Map<String, String> sectionToFirstSymbol = new HashMap<>();

    public LLVMObjectFile(int pageSize, Path tempDir, BigBang bb) {
        super(pageSize);
        this.header = new LLVMHeader("LLVMHeader");
        this.builder = new LLVMIRBuilder("LLVMDataSection");
        basePath = tempDir.resolve("llvm");
        this.bb = bb;
    }

    @Override
    public Format getFormat() {
        return LLVM;
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public void setByteOrder(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    @Override
    protected Segment getOrCreateSegment(String maybeSegmentName, String sectionName, boolean writable, boolean executable) {
        return null;
    }

    @Override
    public Section newUserDefinedSection(Segment segment, String name, int alignment, ElementImpl impl) {
        ElementImpl ourImpl;
        if (impl == null) {
            ourImpl = new BasicProgbitsSectionImpl((Section) null);
        } else {
            ourImpl = impl;
        }
        LLVMUserDefinedSection userDefined = new LLVMUserDefinedSection(this, name, alignment, ourImpl);
        ourImpl.setElement(userDefined);
        return userDefined;
    }

    @Override
    public Section newProgbitsSection(Segment segment, String name, int alignment, boolean writable, boolean executable, ProgbitsSectionImpl impl) {
        LLVMRegularSection progbits = new LLVMRegularSection(this, name, alignment, impl);
        impl.setElement(progbits);
        return progbits;
    }

    @Override
    public Section newNobitsSection(Segment segment, String name, NobitsSectionImpl impl) {
        return null;
    }

    @Override
    public int getWordSizeInBytes() {
        return FrameAccess.wordSize();
    }

    @Override
    public boolean shouldRecordDebugRelocations() {
        return false;
    }

    @Override
    public Set<Segment> getSegments() {
        return new HashSet<>();
    }

    @Override
    protected int getMinimumFileSize() {
        return 0;
    }

    @Override
    public Symbol createDefinedSymbol(String name, Element baseSection, long position, int size, boolean isCode, boolean isGlobal) {
        SymbolTable symtab = getOrCreateSymbolTable();
        return symtab.newDefinedEntry(name, (Section) baseSection, position, size, isGlobal, isCode);
    }

    @Override
    public Symbol createUndefinedSymbol(String name, int size, boolean isCode) {
        SymbolTable symtab = getOrCreateSymbolTable();
        return symtab.newUndefinedEntry(name, isCode);
    }

    @Override
    protected LLVMSymtab createSymbolTable() {
        return new LLVMSymtab(this, "symtab");
    }

    @Override
    public SymbolTable getSymbolTable() {
        return (SymbolTable) elementForName("symtab");
    }

    @Override
    @SuppressWarnings("try")
    public final void write(DebugContext context, Path outputFile) throws IOException {
        List<Element> sortedObjectFileElements = new ArrayList<>();
        bake(sortedObjectFileElements);

        initializeAllSectionParts(sortedObjectFileElements);
        int numBatches = dataSectionParts.size();

        writeParts();

        BatchExecutor batchExecutor = new BatchExecutor(context, bb);

        compileBitcodeBatches(batchExecutor, context, numBatches);

        linkCompiledBatches(context, numBatches);
    }

    private void initializeAllSectionParts(List<ObjectFile.Element> sortedObjectFileElements) {
        int id = 0;

        for (Element e : sortedObjectFileElements) {
            if (e instanceof LLVMUserDefinedSection section) {
                int batchSize = LLVMOptions.LLVMDataSectionBatchSizeFactor.getValue() * SubstrateOptions.getPageSize();
                byte[] content = (byte[]) getDecisionsTaken().get(e).getDecidedValue(LayoutDecision.Kind.CONTENT);

                if (content.length != 0) {
                    ByteBuffer contentByteBuffer = ByteBuffer.wrap(content);
                    contentByteBuffer.order(byteOrder);
                    LongBuffer contentLongBuffer = contentByteBuffer.asLongBuffer();

                    int contentLength = content.length / Long.BYTES;
                    int remainder = content.length % Long.BYTES;
                    boolean hasRemainder = remainder != 0;
                    LLVMValueRef[] valueArray = new LLVMValueRef[contentLength + (hasRemainder ? 1 : 0)];
                    for (int j = 0; j < contentLength; ++j) {
                        valueArray[j] = builder.constantLong(contentLongBuffer.get());
                    }

                    if (hasRemainder) {
                        long lastValue = 0;
                        while (remainder > 0) {
                            lastValue |= content[content.length - remainder - 1];
                            lastValue = lastValue << Byte.SIZE;
                            remainder--;
                        }
                        valueArray[contentLength] = builder.constantLong(byteOrder == ByteOrder.BIG_ENDIAN ? lastValue : Long.reverseBytes(lastValue));
                    }

                    LLVMSymtab symtab = (LLVMSymtab) getSymbolTable();
                    List<LLVMSymtab.Entry> symbols = symtab.getSectionEntries(e.getName());

                    for (int i = 0; i < valueArray.length; i += (batchSize / Long.BYTES)) {
                        LLVMValueRef[] batchContent = Arrays.copyOfRange(valueArray, i, Math.min(valueArray.length, i + (batchSize / Long.BYTES)));
                        int finalI = i;
                        List<Integer> batchRelocOffsets = section.getRelocations().keySet().stream()
                                        .filter(relocOffset -> relocOffset >= finalI * Long.BYTES && relocOffset < finalI * Long.BYTES + batchSize)
                                        .collect(Collectors.toList());
                        LLVMDataSectionPart sectionPart = new LLVMDataSectionPart(id, i * Long.BYTES, getPageSize(), e, batchContent, batchRelocOffsets, i == 0 ? symbols : null);
                        dataSectionParts.add(sectionPart);
                        id++;
                    }
                }
            }
        }
    }

    private void writeParts() {
        for (LLVMDataSectionPart dataSectionPart : dataSectionParts) {
            try (FileOutputStream fos = new FileOutputStream(getBitcodePath(dataSectionPart.getId()).toString())) {
                fos.write(dataSectionPart.getBitcode());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void compileBitcodeBatches(BatchExecutor executor, DebugContext context, int numBatches) {
        executor.forEach(numBatches, batchId -> (debugContextInner) -> {
            llvmCompile(context, getCompiledBitcodeFilename(batchId), getBitcodeFilename(batchId), basePath, (s -> s));
        });
    }

    private void linkCompiledBatches(DebugContext context, int numBatches) {
        List<String> compiledBatches = IntStream.range(0, numBatches).mapToObj(this::getCompiledBitcodeFilename).collect(Collectors.toList());
        nativeLink(context, getLinkedFilename(), compiledBatches, basePath, (s -> s));
    }

    private Path getBitcodePath(int id) {
        return basePath.resolve(getBitcodeFilename(id));
    }

    private static String getBitcodeFilename(int id) {
        return "dataSection" + id + ".bc";
    }

    private String getCompiledBitcodeFilename(int id) {
        return "dataSection" + id + ".o";
    }

    public static String getLinkedFilename() {
        return "dataSection.o";
    }

    public abstract class LLVMSection extends ObjectFile.Section {

        public LLVMSection(String name, int alignment) {
            super(name, alignment);
        }

        public LLVMSection(String name) {
            super(name, getWordSizeInBytes());
        }

        @Override
        public LLVMObjectFile getOwner() {
            return LLVMObjectFile.this;
        }

        @Override
        public boolean isLoadable() {
            return false;
        }

        @Override
        public boolean isReferenceable() {
            if (getImpl() == this) {
                return isLoadable();
            }

            return getImpl().isReferenceable();
        }
    }

    /**
     * LLVM header is only used as dummy for offset computation.
     */
    public class LLVMHeader extends ObjectFile.Header {

        public LLVMHeader(String name) {
            super(name);
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            // our content depends on the section header table size and offset.

            // We don't use the default dependencies, because our offset mustn't depend on anything.
            // Also, our size MUST NOT depend on our content, because other offsets in the file
            // (e.g. SHT, PHT) must be decided before content, and we need to give a size so that
            // that nextAvailableOffset remains defined.
            // So, our size comes first.
            HashSet<BuildDependency> dependencies = new HashSet<>();

            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision ourOffset = decisions.get(this).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision ourSize = decisions.get(this).getDecision(LayoutDecision.Kind.SIZE);

            dependencies.add(BuildDependency.createOrGet(ourOffset, ourSize));
            dependencies.add(BuildDependency.createOrGet(ourContent, ourOffset));

            return dependencies;
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            // We return a dummy word to avoid having another section with an offset of 0.
            return new byte[Long.BYTES];
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            // We are always at 0.
            return 0;
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            // We return a dummy word to avoid having another section with an offset of 0.
            return Long.BYTES;
        }
    }

    public static String getLld() {
        return switch (getNativeFormat()) {
            case ELF -> "ld.lld";
            case PECOFF -> "lld-link";
            case MACH_O -> "ld64.lld";
            case LLVM -> throw VMError.shouldNotReachHere("Cannot have LLVM has native file format as it is linked to OS.");
        };
    }
}
