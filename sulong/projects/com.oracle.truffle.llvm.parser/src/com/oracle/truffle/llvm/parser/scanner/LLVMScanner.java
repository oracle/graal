/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.listeners.BCFileRoot;
import com.oracle.truffle.llvm.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.runtime.Magic;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLVMScanner {

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

    protected long offset;

    protected long oldOffset;

    private LLVMScanner(BitStream bitstream, ParserListener listener) {
        this.bitstream = bitstream;
        this.parser = listener;
        this.block = Block.ROOT;
        this.idSize = DEFAULT_ID_SIZE;
        this.offset = 0;
        this.defaultAbbreviations = new HashMap<>();
    }

    public LLVMScanner(BitStream bitstream, ParserListener parser, Map<Block, List<AbbreviatedRecord[]>> defaultAbbreviations, Block block, int idSize, long offset) {
        assert idSize > 0;
        this.bitstream = bitstream;
        this.defaultAbbreviations = defaultAbbreviations;
        this.block = block;
        this.idSize = idSize;
        this.parser = parser;
        this.offset = offset;
    }

    public static void parseBitcode(ByteSequence bitcode, ModelModule model, Source bcSource) {
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
        fileParser.exit();
    }

    public static class ToEndScanner extends LLVMScanner {
        public ToEndScanner(BitStream bitstream) {
            super(bitstream, null);
        }

        public static long parseToEnd(ByteSequence bitcode) {
            final BitStream bitstream = BitStream.create(bitcode);
            final ToEndScanner scanner = new ToEndScanner(bitstream);
            final long actualMagicWord = scanner.read(Integer.SIZE);
            if (actualMagicWord != Magic.BC_MAGIC_WORD.magic) {
                throw new LLVMParserException("Not a valid Bitcode File!");
            }

            scanner.scanToEnd();
            return scanner.offset / Byte.SIZE;
        }

        @Override
        protected void enterSubBlock(long blockId, int newIdSize, long numWords) {
            assert numWords > 0;
            long startOffset = offset;
            offset += numWords * Integer.SIZE;
            if (offset < startOffset) {
                throw new LLVMParserException("invalid bitcode: overflow or negative size");
            }
        }

        @Override
        protected boolean exitBlock() {
            // return to the beginning of the end block
            offset = oldOffset;
            return true;
        }

        @Override
        protected void unabbreviatedRecord(@SuppressWarnings("unused") RecordBuffer buffer) {
        }
    }

    private static <V> List<V> subList(List<V> original, int from) {
        final List<V> newList = new ArrayList<>(original.size() - from);
        for (int i = from; i < original.size(); i++) {
            newList.add(original.get(i));
        }
        return newList;
    }

    protected long read(int bits) {
        assert bits >= 0;
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
        assert width > 0;
        final long value = bitstream.readVBR(offset, width);
        offset += BitStream.widthVBR(value, width);
        return value;
    }

    protected void scanToEnd() {
        scanToOffset(bitstream.size());
    }

    protected boolean scan() {
        oldOffset = offset;
        final int id = (int) read(idSize);

        switch (id) {
            case BuiltinIDs.END_BLOCK:
                return onEndBlock();

            case BuiltinIDs.ENTER_SUBBLOCK:
                onEnterSubBlock();
                break;

            case BuiltinIDs.DEFINE_ABBREV:
                defineAbbreviation();
                break;

            case BuiltinIDs.UNABBREV_RECORD:
                onUnabbreviatedRecord();
                break;

            default:
                // custom defined abbreviation
                onAbbreviatedRecord(id);
                break;
        }

        return false;
    }

    private void scanToOffset(long to) {
        while (offset < to) {
            if (scan()) {
                return;
            }
        }
    }

    private void onAbbreviatedRecord(int recordId) {
        AbbreviatedRecord[] records = abbreviationDefinitions.get(recordId - BuiltinIDs.CUSTOM_ABBREV_OFFSET);
        for (AbbreviatedRecord record : records) {
            if (record != null) {
                record.scan(this);
            }
        }

        unabbreviatedRecord(recordBuffer);
        recordBuffer.invalidate();
    }

    protected void alignInt() {
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
            assert width >= 0;
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
            assert width > 0;
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
                final long l = Long.compareUnsigned(blobLength, MAX_BLOB_PART_LENGTH) <= 0 ? blobLength : MAX_BLOB_PART_LENGTH;
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

        if (operandCount < 0 || operandCount != (int) operandCount) {
            throw new LLVMParserException("Invalid operand count!");
        }

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
                        final long width = read(Primitive.USER_OPERAND_DATA);
                        if (width < 0 || width != (int) width) {
                            throw new LLVMParserException("invalid bitcode: overflow or negative size");
                        }
                        operandScanners[i] = new FixedAbbreviatedRecord((int) width);
                        break;
                    }

                    case AbbrevRecordId.VBR: {
                        final long width = read(Primitive.USER_OPERAND_DATA);
                        if (width <= 0 || width != (int) width) {
                            throw new LLVMParserException("invalid bitcode: overflow or negative size");
                        }
                        operandScanners[i] = new VBRAbbreviatedRecord((int) width);
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

    protected void enterSubBlock(long blockId, int newIdSize, long numWords) {
        assert numWords > 0;
        assert newIdSize > 0;
        final long endingOffset = offset + (numWords * Integer.SIZE);
        if (endingOffset < offset) {
            throw new LLVMParserException("invalid bitcode: overflow or negative size");
        }

        final Block subBlock = Block.lookup(blockId);
        if (subBlock == null || subBlock.skip()) {
            offset = endingOffset;

        } else if (subBlock.parseLazily()) {
            final LazyScanner lazyScanner = new LazyScanner(bitstream, new HashMap<>(defaultAbbreviations), offset, endingOffset, newIdSize, subBlock);
            offset = endingOffset;
            parser.skip(subBlock, lazyScanner);

        } else {
            final int localAbbreviationDefinitionsOffset = defaultAbbreviations.getOrDefault(block, Collections.emptyList()).size();
            parents.push(new ScannerState(subList(abbreviationDefinitions, localAbbreviationDefinitionsOffset), block, idSize, parser));
            parser = parser.enter(subBlock);
            startSubBlock(subBlock, newIdSize);
        }
    }

    private void onEnterSubBlock() {
        final long blockId = read(Primitive.SUBBLOCK_ID);
        final long newIdSize = read(Primitive.SUBBLOCK_ID_SIZE);
        alignInt();
        final long numWords = read(Integer.SIZE);

        if (numWords <= 0 || newIdSize <= 0 || newIdSize != (int) newIdSize) {
            // overflow
            throw new LLVMParserException("invalid bitcode: overflow or negative size");
        }
        enterSubBlock(blockId, (int) newIdSize, numWords);
    }

    private void startSubBlock(Block subBlock, int newIdSize) {
        abbreviationDefinitions.clear();
        abbreviationDefinitions.addAll(defaultAbbreviations.getOrDefault(subBlock, Collections.emptyList()));
        block = subBlock;

        assert newIdSize > 0;
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

    protected boolean exitBlock() {
        parser.exit();

        if (parents.isEmpty()) {
            // after lazily parsed block
            return false;
        }

        final ScannerState parentState = parents.pop();
        block = parentState.getBlock();

        abbreviationDefinitions.clear();
        abbreviationDefinitions.addAll(defaultAbbreviations.getOrDefault(block, Collections.emptyList()));
        abbreviationDefinitions.addAll(parentState.getAbbreviatedRecords());

        idSize = parentState.getIdSize();
        parser = parentState.getParser();

        return false;
    }

    private boolean onEndBlock() {
        alignInt();
        return exitBlock();
    }

    protected void unabbreviatedRecord(RecordBuffer buffer) {
        parser.record(buffer);
    }

    private void onUnabbreviatedRecord() {
        final long recordId = read(Primitive.UNABBREVIATED_RECORD_ID);
        recordBuffer.addOp(recordId);

        final long opCount = read(Primitive.UNABBREVIATED_RECORD_OPS);
        recordBuffer.ensureFits(opCount);

        long op;
        for (int i = 0; i < opCount; i++) {
            op = read(Primitive.UNABBREVIATED_RECORD_OPERAND);
            recordBuffer.addOpNoCheck(op);
        }

        unabbreviatedRecord(recordBuffer);
        recordBuffer.invalidate();
    }

    public static final class LazyScanner {

        private final BitStream bitstream;
        private final Map<Block, List<AbbreviatedRecord[]>> oldDefaultAbbreviations;
        private final long startingOffset;
        private final long endingOffset;
        private final int startingIdSize;
        private final Block startingBlock;

        private LazyScanner(BitStream bitstream, Map<Block, List<AbbreviatedRecord[]>> oldDefaultAbbreviations, long startingOffset, long endingOffset, int startingIdSize, Block startingBlock) {
            assert startingIdSize > 0;
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
