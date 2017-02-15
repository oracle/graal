/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.parser.listeners.IRVersionController;
import com.oracle.truffle.llvm.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LLVMScanner {

    private static final String CHAR6 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._";

    private static final int DEFAULT_ID_SIZE = 2;

    private static final long MAGIC_WORD = 0xdec04342L; // 'BC' c0de

    private static final int MAX_BLOCK_DEPTH = 3;

    private final List<List<AbbreviatedRecord>> abbreviationDefinitions = new ArrayList<>();

    private final BitStream bitstream;

    private final Map<Block, List<List<AbbreviatedRecord>>> defaultAbbreviations = new HashMap<>();

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

    long read(int bits) {
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

    private long readVBR(long width) {
        final long value = bitstream.readVBR(offset, width);
        offset += BitStream.widthVBR(value, width);
        return value;
    }

    private void scanNext() {
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

    private void abbreviatedRecord(int recordId) {
        abbreviationDefinitions.get(recordId - BuiltinIDs.CUSTOM_ABBREV_OFFSET).forEach(AbbreviatedRecord::scan);
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

        final List<AbbreviatedRecord> operandScanners = new ArrayList<>((int) operandCount);

        int i = 0;
        boolean containsArrayOperand = false;
        while (i < operandCount) {
            // first operand contains the record id

            final boolean isLiteral = read(Primitive.USER_OPERAND_LITERALBIT) == 1;
            if (isLiteral) {
                final long fixedValue = read(Primitive.USER_OPERAND_LITERAL);
                operandScanners.add(() -> recordBuffer.addOpWithCheck(fixedValue));

            } else {

                final long recordType = read(Primitive.USER_OPERAND_TYPE);

                switch ((int) recordType) {
                    case AbbrevRecordId.FIXED: {
                        final int width = (int) read(Primitive.USER_OPERAND_DATA);
                        operandScanners.add(() -> {
                            final long op = read(width);
                            recordBuffer.addOpWithCheck(op);
                        });
                        break;
                    }

                    case AbbrevRecordId.VBR: {
                        final int width = (int) read(Primitive.USER_OPERAND_DATA);
                        operandScanners.add(() -> {
                            final long op = readVBR(width);
                            recordBuffer.addOpWithCheck(op);
                        });
                        break;
                    }

                    case AbbrevRecordId.ARRAY:
                        // arrays only occur as the second to last operand in an abbreviation, just
                        // before their element type
                        // then this can only be executed once for any abbreviation
                        containsArrayOperand = true;
                        break;

                    case AbbrevRecordId.CHAR6:
                        operandScanners.add(() -> {
                            final long op = readChar();
                            recordBuffer.addOpWithCheck(op);
                        });
                        break;

                    case AbbrevRecordId.BLOB:
                        operandScanners.add(() -> {
                            // TODO this does not work for blobs of arbitrary size
                            final long blobLength = read(Primitive.USER_OPERAND_BLOB_LENGTH);
                            alignInt();
                            final long blobValue = read((int) (Primitive.USER_OPERAND_LITERAL.getBits() * blobLength));
                            recordBuffer.addOpWithCheck(blobValue);
                        });
                        break;

                    default:
                        throw new IllegalStateException("Unexpected Record Type Id: " + recordType);
                }

            }

            i++;
        }

        if (containsArrayOperand) {
            final AbbreviatedRecord elementScanner = operandScanners.get(operandScanners.size() - 1);
            final AbbreviatedRecord arrayScanner = () -> {
                final long arrayLength = read(Primitive.USER_OPERAND_ARRAY_LENGTH);
                for (int j = 0; j < arrayLength; j++) {
                    elementScanner.scan();
                }
            };
            operandScanners.set(operandScanners.size() - 1, arrayScanner);
        }

        abbreviationDefinitions.add(operandScanners);
    }

    private void enterSubBlock() {
        final long blockId = read(Primitive.SUBBLOCK_ID);
        final long newIdSize = read(Primitive.SUBBLOCK_ID_SIZE);
        alignInt();
        final long numWords = read(Integer.SIZE);

        final Block subBlock = Block.lookup(blockId);
        if (subBlock == null) {
            LLVMLogger.info("Skipping unsupported Block: " + blockId);
            offset += numWords * Integer.SIZE;

        } else {
            final int localAbbreviationDefinitionsOffset = defaultAbbreviations.getOrDefault(block, Collections.emptyList()).size();
            parents.push(new ScannerState(subList(abbreviationDefinitions, localAbbreviationDefinitionsOffset), block, idSize, parser));
            abbreviationDefinitions.clear();
            abbreviationDefinitions.addAll(defaultAbbreviations.getOrDefault(subBlock, Collections.emptyList()));
            block = subBlock;
            idSize = (int) newIdSize;
            parser = parser.enter(subBlock);

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
    }

    private void exitBlock() {
        alignInt();
        parser.exit();

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

    private void setOffset(long offset) {
        this.offset = offset;
    }

    private void unabbreviatedRecord() {
        final long recordId = read(Primitive.UNABBREVIATED_RECORD_ID);
        recordBuffer.addOpWithCheck(recordId);

        final long opCount = read(Primitive.UNABBREVIATED_RECORD_OPS);
        recordBuffer.ensureFits(opCount);

        long op;
        for (int i = 0; i < opCount; i++) {
            op = read(Primitive.UNABBREVIATED_RECORD_OPERAND);
            recordBuffer.addOp(op);
        }
        passRecordToParser();
    }

    public static ModelModule parse(IRVersionController version, Source source) {
        final BitStream bitstream = BitStream.create(source);
        final ModelModule model = new ModelModule();
        final LLVMScanner scanner = new LLVMScanner(bitstream, version.createModule(model));

        final StreamInformation bcStreamInfo = StreamInformation.getStreamInformation(bitstream, scanner);
        scanner.setOffset(bcStreamInfo.getOffset());

        final long actualMagicWord = scanner.read(Integer.SIZE);
        if (actualMagicWord != MAGIC_WORD) {
            throw new RuntimeException("Not a valid Bitcode File: " + source);
        }

        while (scanner.offset < bcStreamInfo.totalStreamSize()) {
            scanner.scanNext();
        }

        return model;
    }

    private static <V> List<V> subList(List<V> original, int from) {
        final List<V> newList = new ArrayList<>(original.size() - from);
        for (int i = from; i < original.size(); i++) {
            newList.add(original.get(i));
        }
        return newList;
    }
}
