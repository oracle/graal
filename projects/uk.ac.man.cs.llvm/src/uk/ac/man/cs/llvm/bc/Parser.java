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
/*
 * Copyright (c) 2016 University of Manchester
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.man.cs.llvm.bc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import uk.ac.man.cs.llvm.bc.blocks.Block;
import uk.ac.man.cs.llvm.bc.records.UserRecordArrayOperand;
import uk.ac.man.cs.llvm.bc.records.UserRecordBuilder;
import uk.ac.man.cs.llvm.bc.records.UserRecordOperand;
import uk.ac.man.cs.llvm.util.Pair;

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

    private static final Operation END_BLOCK = (parser) -> {
        return parser.exit();
    };

    private static final Operation ENTER_SUBBLOCK = (parser) -> {
        ParserResult result = parser.read(Primitive.SUBBLOCK_ID);
        long id = result.getValue();

        result = result.getParser().read(Primitive.SUBBLOCK_ID_SIZE);
        long idsize = result.getValue();

        parser = result.getParser().align(Integer.SIZE);

        result = parser.read(Integer.SIZE);
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

        parser = result.getParser().handleRecord(id, operands);

        return parser;
    };

    protected static final Operation[] DEFAULT_OPERATIONS = new Operation[]{
                    END_BLOCK,
                    ENTER_SUBBLOCK,
                    DEFINE_ABBREV,
                    UNABBREV_RECORD
    };

    private static final String CHAR6 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXJZ0123456789._";

    protected final Bitstream stream;

    protected final Block block;

    protected final ParserListener listener;

    protected final Parser parent;

    protected final Operation[][] operations;

    protected final long idsize;

    protected final long offset;

    public Parser(Bitstream stream, Block block, ParserListener listener) {
        this(Objects.requireNonNull(stream), Objects.requireNonNull(block), Objects.requireNonNull(listener), null, new Operation[][]{DEFAULT_OPERATIONS}, 2, 0);
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

    protected Parser instantiate(Parser parser, Bitstream stream, Block block, ParserListener listener, Parser parent, Operation[][] operations, long idsize, long offset) {
        return new Parser(stream, block, listener, parent, operations, idsize, offset);
    }

    public Parser align(long bits) {
        long mask = bits - 1;
        if ((offset & mask) != 0) {
            return offset((offset & ~mask) + bits);
        }
        return this;
    }

    public Parser enter(long id, long size, long idsize) {
        Block subblock = Block.lookup(id);
        if (subblock == null) {
            // Cannot find block so just skip it
            return offset(getOffset() + size * Integer.SIZE);
        } else {
            return subblock.getParser(instantiate(this, stream, subblock, listener.enter(subblock), this, operations, idsize, getOffset()));
        }
    }

    public Parser exit() {
        listener.exit();
        return getParent().offset(align(Integer.SIZE).getOffset());
    }

    public long getOffset() {
        return offset;
    }

    public Parser offset(long offset) {
        return instantiate(this, stream, block, listener, parent, operations, idsize, offset);
    }

    public Operation getOperation(long id) {
        return getOperations(block.getId())[(int) id];
    }

    public Parser operation(Operation operation) {
        return operation(block.getId(), operation, false);
    }

    public Parser operation(long id, Operation operation) {
        return operation(id, operation, true);
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

    protected Operation[] getOperations(long blockid) {
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
        return new ParserResult(offset(offset + Primitive.CHAR6.getBits()), (long) value);
    }

    public ParserResult readId() {
        long value = stream.read(offset, idsize);
        return new ParserResult(offset(offset + idsize), value);
    }

    public ParserResult readVBR(long width) {
        long value = stream.readVBR(offset, width);
        long total = stream.widthVBR(value, width);
        return new ParserResult(offset(offset + total), value);
    }
}
