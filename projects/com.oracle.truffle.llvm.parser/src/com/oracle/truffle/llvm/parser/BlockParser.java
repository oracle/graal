/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.llvm.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.records.UserRecordArrayOperand;
import com.oracle.truffle.llvm.parser.records.UserRecordOperand;
import com.oracle.truffle.llvm.parser.util.Pair;

public abstract class BlockParser {

    public enum Block {
        ROOT(-1),

        BLOCKINFO(0),

        MODULE(8),
        PARAMATTR(9),
        PARAMATTR_GROUP(10),
        CONSTANTS(11),
        FUNCTION(12),
        IDENTIFICATION(13),
        VALUE_SYMTAB(14),
        METADATA(15),
        METADATA_ATTACHMENT(16),
        TYPE(17),
        USELIST(18),
        MODULE_STRTAB(19),
        FUNCTION_SUMMARY(20),
        OPERAND_BUNDLE_TAGS(21),
        METADATA_KIND(22);

        private static Block lookup(long id) {
            if (id == 0) {
                return BLOCKINFO;
            } else if (id >= MODULE.getId() && id <= METADATA_KIND.getId()) {
                // Skip ROOT and BLOCKINFO
                int index = (int) id - (MODULE.getId() - MODULE.ordinal());
                return values()[index];
            }
            return null;
        }

        private BlockParser getParser(Bitstream stream, ParserListener listener, BlockParser parent, Operation[][] operations, long idsize, long offset, long bid) {
            if (this == Block.BLOCKINFO) {
                return new InformationBlockParser(stream, this, listener, parent, operations, idsize, offset, bid);
            } else {
                return new GeneralBlockParser(stream, this, listener, parent, operations, idsize, offset);
            }
        }

        private final int id;

        Block(int id) {
            this.id = id;
        }

        private int getId() {
            return id;
        }

        @Override
        public String toString() {
            return String.format("%s - #%d", name(), getId());
        }
    }

    @FunctionalInterface
    public interface Operation {

        BlockParser apply(BlockParser parser);
    }

    private static final Operation DEFINE_ABBREV = (parser) -> {
        ParserResult result = parser.read(Primitive.ABBREVIATED_RECORD_OPERANDS);
        long count = result.getValue();

        List<UserRecordOperand> operands = new ArrayList<>();

        for (long i = 0; i < count;) {
            Pair<ParserResult, UserRecordOperand> pair = UserRecordOperand.parse(result.getParser());
            result = pair.getFirst();
            UserRecordOperand operand = pair.getSecond();
            if (operand instanceof UserRecordArrayOperand) {
                pair = UserRecordOperand.parse(result.getParser());
                result = pair.getFirst();
                operand = pair.getSecond();
                operands.add(new UserRecordArrayOperand(operand));
                i += 2;
            } else {
                operands.add(operand);
                i++;
            }
        }

        return result.getParser().operation(new UserRecordBuilder(operands));
    };

    private static class UserRecordBuilder implements Operation {

        private final List<UserRecordOperand> operands;

        UserRecordBuilder(List<UserRecordOperand> operands) {
            this.operands = operands;
        }

        @Override
        public BlockParser apply(BlockParser parser) {
            ParserResult result = operands.get(0).get(parser);
            long id = result.getValue();

            long[] ops = new long[operands.size() - 1];

            int idx = 0;
            for (int i = 0; i < operands.size() - 1; i++) {
                result = operands.get(i + 1).get(result.getParser());
                long[] values = result.getValues();
                if (idx + values.length > ops.length) {
                    ops = Arrays.copyOf(ops, idx + values.length);
                } else if (values.length == 0) {
                    ops = Arrays.copyOf(ops, ops.length - 1);
                }
                System.arraycopy(values, 0, ops, idx, values.length);
                idx += values.length;
            }

            result.getParser().handleRecord(id, ops);

            return result.getParser();
        }
    }

    private static final Operation END_BLOCK = BlockParser::exit;

    private static final Operation ENTER_SUBBLOCK = (parser) -> {
        ParserResult result = parser.read(Primitive.SUBBLOCK_ID);
        long id = result.getValue();

        result = result.getParser().read(Primitive.SUBBLOCK_ID_SIZE);
        long idsize = result.getValue();

        BlockParser p = result.getParser().alignInt();

        result = p.read(Integer.SIZE);
        long size = result.getValue();

        return result.getParser().enter(id, size, idsize);
    };

    private static final Operation UNABBREV_RECORD = (parser) -> {
        ParserResult result = parser.read(Primitive.UNABBREVIATED_RECORD_ID);
        long id = result.getValue();

        result = result.getParser().read(Primitive.UNABBREVIATED_RECORD_OPS);
        long count = result.getValue();

        long[] operands = new long[(int) count];

        for (long i = 0; i < count; i++) {
            result = result.getParser().read(Primitive.UNABBREVIATED_RECORD_OPERAND);
            operands[(int) i] = result.getValue();
        }

        return result.getParser().handleRecord(id, operands);
    };

    private static final Operation[] DEFAULT_OPERATIONS = new Operation[]{
                    END_BLOCK,
                    ENTER_SUBBLOCK,
                    DEFINE_ABBREV,
                    UNABBREV_RECORD
    };

    private static final String CHAR6 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._";

    final Bitstream stream;

    final Block block;

    final ParserListener listener;

    final BlockParser parent;

    final Operation[][] operations;

    final long idsize;

    final long offset;

    BlockParser(Bitstream stream, Block block, ParserListener listener, BlockParser parent, Operation[][] operations, long idsize, long offset) {
        this.stream = stream;
        this.block = block;
        this.listener = listener;
        this.parent = parent;
        this.operations = operations;
        this.idsize = idsize;
        this.offset = offset;
    }

    public static BlockParser create(Bitstream stream, ParserListener listener) {
        return new GeneralBlockParser(Objects.requireNonNull(stream), Objects.requireNonNull(Block.ROOT), Objects.requireNonNull(listener), null, new Operation[][]{DEFAULT_OPERATIONS}, 2, 0);
    }

    public static BlockParser create(Bitstream stream, ParserListener listener, long offset) {
        return new GeneralBlockParser(Objects.requireNonNull(stream), Objects.requireNonNull(Block.ROOT), Objects.requireNonNull(listener), null, new Operation[][]{DEFAULT_OPERATIONS}, 2, offset);
    }

    abstract BlockParser enter(long id, long size, long argIdSize);

    abstract BlockParser toOffset(long argOffset);

    abstract BlockParser operation(long id, Operation operation, boolean persist);

    public long getOffset() {
        return offset;
    }

    public Operation getOperation(long id) {
        return getOperations(block.getId())[(int) id];
    }

    public ParserResult read(long bits) {
        long value = stream.read(offset, bits);
        return new ParserResult(toOffset(offset + bits), value);
    }

    public ParserResult readId() {
        long value = stream.read(offset, idsize);
        return new ParserResult(toOffset(offset + idsize), value);
    }

    public ParserResult read(Primitive primitive) {
        if (primitive.isFixed()) {
            return read(primitive.getBits());
        } else {
            return readVBR(primitive.getBits());
        }
    }

    public BlockParser alignInt() {
        long mask = Integer.SIZE - 1;
        if ((offset & mask) != 0) {
            return toOffset((offset & ~mask) + Integer.SIZE);
        }
        return this;
    }

    public BlockParser operation(Operation operation) {
        return operation(block.getId(), operation, false);
    }

    public BlockParser handleRecord(long id, long[] operands) {
        listener.record(id, operands);
        return this;
    }

    public ParserResult readChar() {
        char value = CHAR6.charAt((int) stream.read(offset, Primitive.CHAR6.getBits()));
        return new ParserResult(toOffset(offset + Primitive.CHAR6.getBits()), value);
    }

    public ParserResult readVBR(long width) {
        long value = stream.readVBR(offset, width);
        long total = Bitstream.widthVBR(value, width);
        return new ParserResult(toOffset(offset + total), value);
    }

    Operation[] getOperations(long blockid) {
        if (blockid < 0 || blockid >= operations.length || operations[(int) blockid] == null) {
            return DEFAULT_OPERATIONS;
        }
        return operations[(int) blockid];
    }

    private BlockParser exit() {
        listener.exit();
        return parent.toOffset(alignInt().getOffset());
    }

    private static final class GeneralBlockParser extends BlockParser {

        GeneralBlockParser(Bitstream stream, Block block, ParserListener listener, BlockParser parent, Operation[][] operations, long idsize, long offset) {
            super(stream, block, listener, parent, operations, idsize, offset);
        }

        @Override
        BlockParser enter(long id, long size, long argIdSize) {
            Block subblock = Block.lookup(id);
            if (subblock == null) {
                // Cannot find block so just skip it
                return toOffset(getOffset() + size * Integer.SIZE);
            } else {
                return subblock.getParser(stream, listener.enter(subblock), this, operations, argIdSize, getOffset(), 0);
            }
        }

        @Override
        protected BlockParser operation(long id, Operation operation, boolean persist) {
            Operation[] oldOps = getOperations(id);
            Operation[] newOps = Arrays.copyOf(oldOps, oldOps.length + 1);

            newOps[oldOps.length] = operation;

            Operation[][] ops = Arrays.copyOf(operations, Math.max(((int) id) + 1, operations.length));
            ops[(int) id] = newOps;

            BlockParser par = parent;
            if (persist) {
                par = parent.operation(id, operation, parent.parent != null);
            }
            return new GeneralBlockParser(stream, block, listener, par, ops, idsize, offset);
        }

        @Override
        BlockParser toOffset(long argOffset) {
            return new GeneralBlockParser(stream, block, listener, parent, operations, idsize, argOffset);
        }
    }

    private static final class InformationBlockParser extends BlockParser {

        private final long bid;

        private InformationBlockParser(Bitstream stream, Block block, ParserListener listener, BlockParser parent, Operation[][] operations, long idsize, long offset, long bid) {
            super(stream, block, listener, parent, operations, idsize, offset);
            this.bid = bid;
        }

        @Override
        public BlockParser operation(Operation operation) {
            return operation(bid, operation, true);
        }

        @Override
        public BlockParser handleRecord(long id, long[] operands) {
            if (id == 1) {
                // SETBID selects which block subsequent abbreviations are assigned
                return new InformationBlockParser(stream, block, listener, parent, operations, idsize, offset, operands[0]);
            } else {
                return super.handleRecord(id, operands);
            }
        }

        @Override
        BlockParser enter(long id, long size, long argIdSize) {
            Block subblock = Block.lookup(id);
            if (subblock == null) {
                // Cannot find block so just skip it
                return toOffset(getOffset() + size * Integer.SIZE);
            } else {
                return subblock.getParser(stream, listener.enter(subblock), this, operations, argIdSize, getOffset(), bid);
            }
        }

        @Override
        protected BlockParser operation(long id, Operation operation, boolean persist) {
            Operation[] oldOps = getOperations(id);
            Operation[] newOps = Arrays.copyOf(oldOps, oldOps.length + 1);

            newOps[oldOps.length] = operation;

            Operation[][] ops = Arrays.copyOf(operations, Math.max(((int) id) + 1, operations.length));
            ops[(int) id] = newOps;

            BlockParser par = parent;
            if (persist) {
                par = parent.operation(id, operation, parent.parent != null);
            }
            return new InformationBlockParser(stream, block, listener, par, ops, idsize, offset, bid);
        }

        @Override
        BlockParser toOffset(long argOffset) {
            return new InformationBlockParser(stream, block, listener, parent, operations, idsize, argOffset, bid);
        }
    }

}
