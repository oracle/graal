/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.listeners.BCFileRoot;
import com.oracle.truffle.llvm.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.Magic;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;

public final class LLVMScanner {

    private static final String CHAR6 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._";

    private static final int DEFAULT_ID_SIZE = 2;

    private static final int MAX_BLOCK_DEPTH = 3;

    private final BitStream bitstream;

    private ParserListener parser;

    private final Map<Block, List<AbbreviatedRecord[]>> defaultAbbreviations;

    private final List<AbbreviatedRecord[]> abbreviationDefinitions = new ArrayList<>();

    private final Deque<ScannerState> parents = new ArrayDeque<>(MAX_BLOCK_DEPTH);

    private final RecordBuffer recordBuffer = new RecordBuffer();

    private Block block;

    private int idSize;

    private long offset;

    private LLVMScanner(BitStream bitstream, ParserListener listener) {
        this.bitstream = bitstream;
        this.parser = listener;
        this.block = Block.ROOT;
        this.idSize = DEFAULT_ID_SIZE;
        this.offset = 0;
        this.defaultAbbreviations = new HashMap<>();
    }

    public LLVMScanner(BitStream bitstream, ParserListener parser, Map<Block, List<AbbreviatedRecord[]>> defaultAbbreviations, Block block, int idSize, long offset) {
        this.bitstream = bitstream;
        this.defaultAbbreviations = defaultAbbreviations;
        this.block = block;
        this.idSize = idSize;
        this.parser = parser;
        this.offset = offset;
    }

    public static void parseBitcode(ByteSequence bitcode, ModelModule model, Source bcSource, LLVMContext context) {
        final BitStream bitstream = BitStream.create(bitcode);
        final BCFileRoot fileParser = new BCFileRoot(model, bcSource);
        final LLVMScanner scanner = new LLVMScanner(bitstream, fileParser);
        final long actualMagicWord = scanner.read(Integer.SIZE);
        if (actualMagicWord != Magic.BC_MAGIC_WORD.magic) {
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
                record.scan(this);
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

    private static final class ConstantAbbreviatedRecord implements AbbreviatedRecord {

        private final long value;

        ConstantAbbreviatedRecord(long value) {
            this.value = value;
        }

        @Override
        public void scan(LLVMScanner scanner) {
            scanner.recordBuffer.addOp(value);
        }
    }

    private static final class FixedAbbreviatedRecord implements AbbreviatedRecord {

        private final int width;

        FixedAbbreviatedRecord(int width) {
            this.width = width;
        }

        @Override
        public void scan(LLVMScanner scanner) {
            scanner.recordBuffer.addOp(scanner.read(width));
        }
    }

    private static final class VBRAbbreviatedRecord implements AbbreviatedRecord {

        private final int width;

        VBRAbbreviatedRecord(int width) {
            this.width = width;
        }

        @Override
        public void scan(LLVMScanner scanner) {
            scanner.recordBuffer.addOp(scanner.readVBR(width));
        }
    }

    private static final class Char6AbbreviatedRecord implements AbbreviatedRecord {

        private static final Char6AbbreviatedRecord INSTANCE = new Char6AbbreviatedRecord();

        @Override
        public void scan(LLVMScanner scanner) {
            scanner.recordBuffer.addOp(scanner.readChar());
        }
    }

    private static final class BlobAbbreviatedRecord implements AbbreviatedRecord {

        private static final BlobAbbreviatedRecord INSTANCE = new BlobAbbreviatedRecord();
        private static final long MAX_BLOB_PART_LENGTH = Long.SIZE / Primitive.USER_OPERAND_LITERAL.getBits();

        @Override
        public void scan(LLVMScanner scanner) {
            long blobLength = scanner.read(Primitive.USER_OPERAND_BLOB_LENGTH);
            scanner.alignInt();
            scanner.recordBuffer.ensureFits(blobLength / MAX_BLOB_PART_LENGTH);
            while (blobLength > 0) {
                final long l = blobLength <= MAX_BLOB_PART_LENGTH ? blobLength : MAX_BLOB_PART_LENGTH;
                final long blobValue = scanner.read((int) (Primitive.USER_OPERAND_LITERAL.getBits() * l));
                scanner.recordBuffer.addOp(blobValue);
                blobLength -= l;
            }
            scanner.alignInt();
        }
    }

    private static final class ArrayAbbreviatedRecord implements AbbreviatedRecord {

        private final AbbreviatedRecord elementScanner;

        ArrayAbbreviatedRecord(AbbreviatedRecord elementScanner) {
            this.elementScanner = elementScanner;
        }

        @Override
        public void scan(LLVMScanner scanner) {
            final long arrayLength = scanner.read(Primitive.USER_OPERAND_ARRAY_LENGTH);
            scanner.recordBuffer.ensureFits(arrayLength);
            for (int j = 0; j < arrayLength; j++) {
                elementScanner.scan(scanner);
            }
        }
    }

    private void defineAbbreviation() {
        final long operandCount = read(Primitive.ABBREVIATED_RECORD_OPERANDS);

        AbbreviatedRecord[] operandScanners = new AbbreviatedRecord[(int) operandCount];

        boolean containsArrayOperand = false;
        for (int i = 0; i < operandCount; i++) {
            // first operand contains the record id

            final boolean isLiteral = read(Primitive.USER_OPERAND_LITERALBIT) == 1;
            if (isLiteral) {
                final long fixedValue = read(Primitive.USER_OPERAND_LITERAL);
                operandScanners[i] = new ConstantAbbreviatedRecord(fixedValue);

            } else {

                final long recordType = read(Primitive.USER_OPERAND_TYPE);

                switch ((int) recordType) {
                    case AbbrevRecordId.FIXED: {
                        final int width = (int) read(Primitive.USER_OPERAND_DATA);
                        operandScanners[i] = new FixedAbbreviatedRecord(width);
                        break;
                    }

                    case AbbrevRecordId.VBR: {
                        final int width = (int) read(Primitive.USER_OPERAND_DATA);
                        operandScanners[i] = new VBRAbbreviatedRecord(width);
                        break;
                    }

                    case AbbrevRecordId.ARRAY:
                        // arrays only occur as the second to last operand in an abbreviation, just
                        // before their element type
                        // then this can only be executed once for any abbreviation
                        containsArrayOperand = true;
                        break;

                    case AbbrevRecordId.CHAR6:
                        operandScanners[i] = Char6AbbreviatedRecord.INSTANCE;
                        break;

                    case AbbrevRecordId.BLOB:
                        operandScanners[i] = BlobAbbreviatedRecord.INSTANCE;
                        break;

                    default:
                        throw new LLVMParserException("Unknown ID in for record abbreviation: " + recordType);
                }
            }
        }

        if (containsArrayOperand) {
            final AbbreviatedRecord elementScanner = operandScanners[operandScanners.length - 1];
            final AbbreviatedRecord arrayScanner = new ArrayAbbreviatedRecord(elementScanner);
            operandScanners[operandScanners.length - 1] = arrayScanner;
        }

        abbreviationDefinitions.add(operandScanners);
    }

    private void enterSubBlock() {
        final long blockId = read(Primitive.SUBBLOCK_ID);
        final long newIdSize = read(Primitive.SUBBLOCK_ID_SIZE);
        alignInt();
        final long numWords = read(Integer.SIZE);
        final long endingOffset = offset + (numWords * Integer.SIZE);

        final Block subBlock = Block.lookup(blockId);
        if (subBlock == null || subBlock.skip()) {
            offset = endingOffset;

        } else if (subBlock.parseLazily()) {
            final LazyScanner lazyScanner = new LazyScanner(bitstream, new HashMap<>(defaultAbbreviations), offset, endingOffset, (int) newIdSize, subBlock);
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
                public void record(RecordBuffer buffer) {
                    if (buffer.getId() == 1) {
                        // SETBID tells us which blocks is currently being described
                        // we simply ignore SETRECORDNAME since we do not need it
                        setDefaultAbbreviations();
                        currentBlockId = (int) buffer.getAt(0);
                    }
                    parentListener.record(buffer);
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
        parser.record(recordBuffer);
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

    public static final class LazyScanner {

        private final BitStream bitstream;
        private final Map<Block, List<AbbreviatedRecord[]>> oldDefaultAbbreviations;
        private final long startingOffset;
        private final long endingOffset;
        private final int startingIdSize;
        private final Block startingBlock;

        private LazyScanner(BitStream bitstream, Map<Block, List<AbbreviatedRecord[]>> oldDefaultAbbreviations, long startingOffset, long endingOffset, int startingIdSize, Block startingBlock) {
            this.bitstream = bitstream;
            this.oldDefaultAbbreviations = oldDefaultAbbreviations;
            this.startingOffset = startingOffset;
            this.endingOffset = endingOffset;
            this.startingIdSize = startingIdSize;
            this.startingBlock = startingBlock;
        }

        public void scanBlock(ParserListener parser) {
            LLVMScanner scanner = new LLVMScanner(bitstream, parser, new HashMap<>(oldDefaultAbbreviations), startingBlock, startingIdSize, startingOffset);
            scanner.startSubBlock(startingBlock, startingIdSize);
            scanner.scanToOffset(endingOffset);
        }
    }
}
