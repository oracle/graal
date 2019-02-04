/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.parser.scanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.elf.ElfDynamicSection;
import com.oracle.truffle.llvm.parser.elf.ElfFile;
import com.oracle.truffle.llvm.parser.elf.ElfSectionHeaderTable.Entry;
import com.oracle.truffle.llvm.parser.listeners.BCFileRoot;
import com.oracle.truffle.llvm.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;

public final class LLVMScanner {

    private static final String CHAR6 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._";

    private static final int DEFAULT_ID_SIZE = 2;

    private static final long BC_MAGIC_WORD = 0xdec04342L; // 'BC' c0de
    private static final long WRAPPER_MAGIC_WORD = 0x0B17C0DEL;
    private static final long ELF_MAGIC_WORD = 0x464C457FL;

    private static final int MAX_BLOCK_DEPTH = 3;

    private final List<AbbreviatedRecord[]> abbreviationDefinitions = new ArrayList<>();

    private final BitStream bitstream;

    private final Map<Block, List<AbbreviatedRecord[]>> defaultAbbreviations = new HashMap<>();

    private final Deque<ScannerState> parents = new ArrayDeque<>(MAX_BLOCK_DEPTH);

    private final RecordBuffer recordBuffer = new RecordBuffer();

    private Block block;

    private int idSize;

    private ParserListener parser;

    private long offset;

    private LLVMScanner(BitStream bitstream, ParserListener listener) {
        this.bitstream = bitstream;
        this.parser = listener;
        this.block = Block.ROOT;
        this.idSize = DEFAULT_ID_SIZE;
        this.offset = 0;
    }

    public static ModelModule parse(ByteSequence bytes, Source bcSource, LLVMContext context) {
        assert bytes != null;
        if (!isSupportedFile(bytes)) {
            return null;
        }

        final ModelModule model = new ModelModule();

        BitStream b = BitStream.create(bytes);
        ByteSequence bitcode;
        // 0: magic word
        long magicWord = Integer.toUnsignedLong((int) b.read(0, Integer.SIZE));
        if (Long.compareUnsigned(magicWord, BC_MAGIC_WORD) == 0) {
            bitcode = bytes;
        } else if (magicWord == WRAPPER_MAGIC_WORD) {
            // 32: version
            // 64: offset32
            long offset = b.read(64, Integer.SIZE);
            // 96: size32
            long size = b.read(96, Integer.SIZE);
            bitcode = bytes.subSequence((int) offset, (int) (offset + size));
        } else if (magicWord == ELF_MAGIC_WORD) {
            ElfFile elfFile = ElfFile.create(bytes);
            Entry llvmbc = elfFile.getSectionHeaderTable().getEntry(".llvmbc");
            if (llvmbc == null) {
                // ELF File does not contain an .llvmbc section
                return null;
            }
            ElfDynamicSection dynamicSection = elfFile.getDynamicSection();
            if (dynamicSection != null) {
                List<String> libraries = dynamicSection.getDTNeeded();
                List<String> paths = dynamicSection.getDTRPath();
                model.addLibraries(libraries);
                model.addLibraryPaths(paths);
            }
            long offset = llvmbc.getOffset();
            long size = llvmbc.getSize();
            bitcode = bytes.subSequence((int) offset, (int) (offset + size));
        } else {
            throw new LLVMParserException("Not a valid input file!");
        }

        parseBitcodeBlock(bitcode, model, bcSource, context);

        return model;
    }

    private static boolean isSupportedFile(ByteSequence bytes) {
        BitStream bs = BitStream.create(bytes);
        try {
            long magicWord = bs.read(0, Integer.SIZE);
            return magicWord == BC_MAGIC_WORD || magicWord == WRAPPER_MAGIC_WORD || magicWord == ELF_MAGIC_WORD;
        } catch (Exception e) {
            /*
             * An exception here means we can't read at least 4 bytes from the file. That means it
             * is definitely not a bitcode or ELF file.
             */
            return false;
        }
    }

    private static void parseBitcodeBlock(ByteSequence bitcode, ModelModule model, Source bcSource, LLVMContext context) {
        final BitStream bitstream = BitStream.create(bitcode);
        final BCFileRoot fileParser = new BCFileRoot(model, bcSource);
        final LLVMScanner scanner = new LLVMScanner(bitstream, fileParser);
        final long actualMagicWord = scanner.read(Integer.SIZE);
        if (actualMagicWord != BC_MAGIC_WORD) {
            throw new LLVMParserException("Not a valid Bitcode File!");
        }

        scanner.scanToEnd();

        // the root block does not exist in the LLVM file and is therefore never exited by the
        // scanner
        fileParser.exit(context);
    }

    private static <V> List<V> subList(List<V> original, int from) {
        final List<V> newList = new ArrayList<>(original.size() - from);
        for (int i = from; i < original.size(); i++) {
            newList.add(original.get(i));
        }
        return newList;
    }

    private long read(int bits) {
        final long value = bitstream.read(offset, bits);
        offset += bits;
        return value;
    }

    private long read(Primitive primitive) {
        if (primitive.isFixed()) {
            return read(primitive.getBits());
        } else {
            return readVBR(primitive.getBits());
        }
    }

    private long readChar() {
        final long value = read(Primitive.CHAR6);
        return CHAR6.charAt((int) value);
    }

    private long readVBR(int width) {
        final long value = bitstream.readVBR(offset, width);
        offset += BitStream.widthVBR(value, width);
        return value;
    }

    private void scanToEnd() {
        scanToOffset(bitstream.size());
    }

    private void scanToOffset(long to) {
        while (offset < to) {
            final int id = (int) read(idSize);

            switch (id) {
                case BuiltinIDs.END_BLOCK:
                    exitBlock();
                    break;

                case BuiltinIDs.ENTER_SUBBLOCK:
                    enterSubBlock();
                    break;

                case BuiltinIDs.DEFINE_ABBREV:
                    defineAbbreviation();
                    break;

                case BuiltinIDs.UNABBREV_RECORD:
                    unabbreviatedRecord();
                    break;

                default:
                    // custom defined abbreviation
                    abbreviatedRecord(id);
                    break;
            }
        }
    }

    private void abbreviatedRecord(int recordId) {
        AbbreviatedRecord[] records = abbreviationDefinitions.get(recordId - BuiltinIDs.CUSTOM_ABBREV_OFFSET);
        for (AbbreviatedRecord record : records) {
            if (record != null) {
                record.scan();
            }
        }
        passRecordToParser();
    }

    private void alignInt() {
        long mask = Integer.SIZE - 1;
        if ((offset & mask) != 0) {
            offset = (offset & ~mask) + Integer.SIZE;
        }
    }

    private void defineAbbreviation() {
        final long operandCount = read(Primitive.ABBREVIATED_RECORD_OPERANDS);

        AbbreviatedRecord[] operandScanners = new AbbreviatedRecord[(int) operandCount];

        int i = 0;
        boolean containsArrayOperand = false;
        while (i < operandCount) {
            // first operand contains the record id

            final boolean isLiteral = read(Primitive.USER_OPERAND_LITERALBIT) == 1;
            if (isLiteral) {
                final long fixedValue = read(Primitive.USER_OPERAND_LITERAL);
                operandScanners[i] = () -> recordBuffer.addOp(fixedValue);

            } else {

                final long recordType = read(Primitive.USER_OPERAND_TYPE);

                switch ((int) recordType) {
                    case AbbrevRecordId.FIXED: {
                        final int width = (int) read(Primitive.USER_OPERAND_DATA);
                        operandScanners[i] = () -> {
                            final long op = read(width);
                            recordBuffer.addOp(op);
                        };
                        break;
                    }

                    case AbbrevRecordId.VBR: {
                        final int width = (int) read(Primitive.USER_OPERAND_DATA);
                        operandScanners[i] = () -> {
                            final long op = readVBR(width);
                            recordBuffer.addOp(op);
                        };
                        break;
                    }

                    case AbbrevRecordId.ARRAY:
                        // arrays only occur as the second to last operand in an abbreviation, just
                        // before their element type
                        // then this can only be executed once for any abbreviation
                        containsArrayOperand = true;
                        break;

                    case AbbrevRecordId.CHAR6:
                        operandScanners[i] = () -> {
                            final long op = readChar();
                            recordBuffer.addOp(op);
                        };
                        break;

                    case AbbrevRecordId.BLOB:
                        operandScanners[i] = () -> {
                            long blobLength = read(Primitive.USER_OPERAND_BLOB_LENGTH);
                            alignInt();
                            final long maxBlobPartLength = Long.SIZE / Primitive.USER_OPERAND_LITERAL.getBits();
                            recordBuffer.ensureFits(blobLength / maxBlobPartLength);
                            while (blobLength > 0) {
                                final long l = blobLength <= maxBlobPartLength ? blobLength : maxBlobPartLength;
                                final long blobValue = read((int) (Primitive.USER_OPERAND_LITERAL.getBits() * l));
                                recordBuffer.addOp(blobValue);
                                blobLength -= l;
                            }
                            alignInt();
                        };
                        break;

                    default:
                        throw new LLVMParserException("Unknown ID in for record abbreviation: " + recordType);
                }
            }

            i++;
        }

        if (containsArrayOperand) {
            final AbbreviatedRecord elementScanner = operandScanners[operandScanners.length - 1];
            final AbbreviatedRecord arrayScanner = () -> {
                final long arrayLength = read(Primitive.USER_OPERAND_ARRAY_LENGTH);
                recordBuffer.ensureFits(arrayLength);
                for (int j = 0; j < arrayLength; j++) {
                    elementScanner.scan();
                }
            };
            operandScanners[operandScanners.length - 1] = arrayScanner;
        }

        abbreviationDefinitions.add(operandScanners);
    }

    private void enterSubBlock() {
        final long blockId = read(Primitive.SUBBLOCK_ID);
        final long newIdSize = read(Primitive.SUBBLOCK_ID_SIZE);
        alignInt();
        final long numWords = read(Integer.SIZE);

        final Block subBlock = Block.lookup(blockId);
        if (subBlock == null || subBlock.skip()) {
            offset += numWords * Integer.SIZE;

        } else if (subBlock.parseLazily()) {
            final long endingOffset = offset + (numWords * Integer.SIZE);
            final LazyScanner lazyScanner = new LazyScanner(new HashMap<>(defaultAbbreviations), offset, endingOffset, (int) newIdSize, subBlock);
            offset = endingOffset;
            parser.skip(subBlock, lazyScanner);

        } else {
            final int localAbbreviationDefinitionsOffset = defaultAbbreviations.getOrDefault(block, Collections.emptyList()).size();
            parents.push(new ScannerState(subList(abbreviationDefinitions, localAbbreviationDefinitionsOffset), block, idSize, parser));
            parser = parser.enter(subBlock);
            startSubBlock(subBlock, (int) newIdSize);
        }
    }

    private void startSubBlock(Block subBlock, int newIdSize) {
        abbreviationDefinitions.clear();
        abbreviationDefinitions.addAll(defaultAbbreviations.getOrDefault(subBlock, Collections.emptyList()));
        block = subBlock;
        idSize = newIdSize;

        if (block == Block.BLOCKINFO) {
            final ParserListener parentListener = parser;
            parser = new ParserListener() {

                int currentBlockId = -1;

                @Override
                public ParserListener enter(Block newBlock) {
                    return parentListener.enter(newBlock);
                }

                @Override
                public void exit() {
                    setDefaultAbbreviations();
                    parentListener.exit();
                }

                @Override
                public void record(long id, long[] args) {
                    if (id == 1) {
                        // SETBID tells us which blocks is currently being described
                        // we simply ignore SETRECORDNAME since we do not need it
                        setDefaultAbbreviations();
                        currentBlockId = (int) args[0];
                    }
                    parentListener.record(id, args);
                }

                private void setDefaultAbbreviations() {
                    if (currentBlockId >= 0) {
                        final Block currentBlock = Block.lookup(currentBlockId);
                        defaultAbbreviations.putIfAbsent(currentBlock, new ArrayList<>());
                        defaultAbbreviations.get(currentBlock).addAll(abbreviationDefinitions);
                        abbreviationDefinitions.clear();
                    }
                }
            };
        }
    }

    private void exitBlock() {
        alignInt();
        parser.exit();

        if (parents.isEmpty()) {
            // after lazily parsed block
            return;
        }

        final ScannerState parentState = parents.pop();
        block = parentState.getBlock();

        abbreviationDefinitions.clear();
        abbreviationDefinitions.addAll(defaultAbbreviations.getOrDefault(block, Collections.emptyList()));
        abbreviationDefinitions.addAll(parentState.getAbbreviatedRecords());

        idSize = parentState.getIdSize();
        parser = parentState.getParser();
    }

    private void passRecordToParser() {
        parser.record(recordBuffer.getId(), recordBuffer.getOps());
        recordBuffer.invalidate();
    }

    private void unabbreviatedRecord() {
        final long recordId = read(Primitive.UNABBREVIATED_RECORD_ID);
        recordBuffer.addOp(recordId);

        final long opCount = read(Primitive.UNABBREVIATED_RECORD_OPS);
        recordBuffer.ensureFits(opCount);

        long op;
        for (int i = 0; i < opCount; i++) {
            op = read(Primitive.UNABBREVIATED_RECORD_OPERAND);
            recordBuffer.addOpNoCheck(op);
        }
        passRecordToParser();
    }

    public final class LazyScanner {

        private final Map<Block, List<AbbreviatedRecord[]>> oldDefaultAbbreviations;
        private final long startingOffset;
        private final long endingOffset;
        private final int startingIdSize;
        private final Block startingBlock;

        private LazyScanner(Map<Block, List<AbbreviatedRecord[]>> oldDefaultAbbreviations, long startingOffset, long endingOffset, int startingIdSize, Block startingBlock) {
            this.oldDefaultAbbreviations = oldDefaultAbbreviations;
            this.startingOffset = startingOffset;
            this.endingOffset = endingOffset;
            this.startingIdSize = startingIdSize;
            this.startingBlock = startingBlock;
        }

        public void scanBlock(ParserListener lazyParser) {
            assert parents.isEmpty();
            defaultAbbreviations.clear();
            defaultAbbreviations.putAll(oldDefaultAbbreviations);
            offset = startingOffset;
            parser = lazyParser;
            startSubBlock(startingBlock, startingIdSize);
            scanToOffset(endingOffset);
        }
    }
}
