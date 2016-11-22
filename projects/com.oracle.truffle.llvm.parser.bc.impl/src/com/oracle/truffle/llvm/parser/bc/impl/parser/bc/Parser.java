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
package com.oracle.truffle.llvm.parser.bc.impl.parser.bc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.blocks.Block;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.records.UserRecordArrayOperand;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.records.UserRecordBuilder;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.records.UserRecordOperand;
import com.oracle.truffle.llvm.parser.bc.impl.parser.listeners.ParserListener;
import com.oracle.truffle.llvm.parser.bc.impl.parser.util.Pair;

public class Parser {

    private static final Operation DEFINE_ABBREV = (parser) -> {
        ParserResult result = parser.read(Primitive.ABBREVIATED_RECORD_OPERANDS);
        long count = result.getValue();

        List<UserRecordOperand> operands = new ArrayList<>();

        for (long i = 0; i < count;) {
            Pair<ParserResult, UserRecordOperand> pair = UserRecordOperand.parse(result.getParser());
            result = pair.getItem1();
            UserRecordOperand operand = pair.getItem2();
            if (operand instanceof UserRecordArrayOperand) {
                pair = UserRecordOperand.parse(result.getParser());
                result = pair.getItem1();
                operand = pair.getItem2();
                operands.add(new UserRecordArrayOperand(operand));
                i += 2;
            } else {
                operands.add(operand);
                i++;
            }
        }

        return result.getParser().operation(new UserRecordBuilder(operands));
    };

    private static final Operation END_BLOCK = Parser::exit;

    private static final Operation ENTER_SUBBLOCK = (parser) -> {
        ParserResult result = parser.read(Primitive.SUBBLOCK_ID);
        long id = result.getValue();

        result = result.getParser().read(Primitive.SUBBLOCK_ID_SIZE);
        long idsize = result.getValue();

        Parser p = result.getParser().alignInt();

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

    protected final Bitstream stream;

    protected final Block block;

    protected final ParserListener listener;

    protected final Parser parent;

    protected final Operation[][] operations;

    protected final long idsize;

    protected final long offset;

    public Parser(Bitstream stream, ParserListener listener) {
        this(Objects.requireNonNull(stream), Objects.requireNonNull(Block.ROOT), Objects.requireNonNull(listener), null, new Operation[][]{DEFAULT_OPERATIONS}, 2, 0);
    }

    public Parser(Bitstream stream, ParserListener listener, long offset) {
        this(Objects.requireNonNull(stream), Objects.requireNonNull(Block.ROOT), Objects.requireNonNull(listener), null, new Operation[][]{DEFAULT_OPERATIONS}, 2, offset);
    }

    protected Parser(Parser parser) {
        this(parser.stream, parser.block, parser.listener, parser.parent, parser.operations, parser.idsize, parser.offset);
    }

    protected Parser(Bitstream stream, Block block, ParserListener listener, Parser parent, Operation[][] operations, long idsize, long offset) {
        this.stream = stream;
        this.block = block;
        this.listener = listener;
        this.parent = parent;
        this.operations = operations;
        this.idsize = idsize;
        this.offset = offset;
    }

    protected Parser instantiate(@SuppressWarnings("unused") Parser argParser, Bitstream argStream, Block argBlock, ParserListener argListener, Parser argParent, Operation[][] argOperations,
                    long argIdSize, long argOffset) {
        return new Parser(argStream, argBlock, argListener, argParent, argOperations, argIdSize, argOffset);
    }

    private Parser alignInt() {
        long mask = Integer.SIZE - 1;
        if ((offset & mask) != 0) {
            return offset((offset & ~mask) + Integer.SIZE);
        }
        return this;
    }

    public Parser enter(long id, long size, long argIdSize) {
        Block subblock = Block.lookup(id);
        if (subblock == null) {
            // Cannot find block so just skip it
            return offset(getOffset() + size * Integer.SIZE);
        } else {
            return subblock.getParser(instantiate(this, stream, subblock, listener.enter(subblock), this, operations, argIdSize, getOffset()));
        }
    }

    public Parser exit() {
        listener.exit();
        return getParent().offset(alignInt().getOffset());
    }

    public long getOffset() {
        return offset;
    }

    public Parser offset(long argOffset) {
        return instantiate(this, stream, block, listener, parent, operations, idsize, argOffset);
    }

    public Operation getOperation(long id) {
        return getOperations(block.getId())[(int) id];
    }

    public Parser operation(Operation operation) {
        return operation(block.getId(), operation, false);
    }

    protected Parser operation(long id, Operation operation, boolean persist) {
        Operation[] oldOps = getOperations(id);
        Operation[] newOps = Arrays.copyOf(oldOps, oldOps.length + 1);

        newOps[oldOps.length] = operation;

        Operation[][] ops = Arrays.copyOf(operations, Math.max(((int) id) + 1, operations.length));
        ops[(int) id] = newOps;

        Parser par = parent;
        if (persist) {
            par = parent.operation(id, operation, parent.parent != null);
        }
        return instantiate(this, stream, block, listener, par, ops, idsize, offset);
    }

    private Operation[] getOperations(long blockid) {
        if (blockid < 0 || blockid >= operations.length || operations[(int) blockid] == null) {
            return DEFAULT_OPERATIONS;
        }
        return operations[(int) blockid];
    }

    public Parser getParent() {
        return parent;
    }

    public Parser handleRecord(long id, long[] operands) {
        listener.record(id, operands);
        return this;
    }

    public ParserResult read(long bits) {
        long value = stream.read(offset, bits);
        return new ParserResult(offset(offset + bits), value);
    }

    public ParserResult read(Primitive primitive) {
        if (primitive.isFixed()) {
            return read(primitive.getBits());
        } else {
            return readVBR(primitive.getBits());
        }
    }

    public ParserResult readChar() {
        char value = CHAR6.charAt((int) stream.read(offset, Primitive.CHAR6.getBits()));
        return new ParserResult(offset(offset + Primitive.CHAR6.getBits()), value);
    }

    public ParserResult readId() {
        long value = stream.read(offset, idsize);
        return new ParserResult(offset(offset + idsize), value);
    }

    public ParserResult readVBR(long width) {
        long value = stream.readVBR(offset, width);
        long total = Bitstream.widthVBR(value, width);
        return new ParserResult(offset(offset + total), value);
    }
}
