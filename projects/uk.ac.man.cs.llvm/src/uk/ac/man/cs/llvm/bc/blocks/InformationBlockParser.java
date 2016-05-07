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
package uk.ac.man.cs.llvm.bc.blocks;

import uk.ac.man.cs.llvm.bc.Bitstream;
import uk.ac.man.cs.llvm.bc.Operation;
import uk.ac.man.cs.llvm.bc.Parser;
import uk.ac.man.cs.llvm.bc.ParserListener;

public final class InformationBlockParser extends Parser {

    private final long bid;

    public InformationBlockParser(Parser parser) {
        super(parser);
        bid = 0;
    }

    protected InformationBlockParser(Bitstream stream, Block block, ParserListener listener, Parser parent, Operation[][] operations, long idsize, long offset, long bid) {
        super(stream, block, listener, parent, operations, idsize, offset);
        this.bid = bid;
    }

    @Override
    protected Parser instantiate(Parser argParser, Bitstream argStream, Block argBlock, ParserListener argListener, Parser argParent, Operation[][] argOperations, long argIdSize, long argOffset) {
        long b = argParser instanceof InformationBlockParser ? ((InformationBlockParser) argParser).bid : 0;
        return new InformationBlockParser(argStream, argBlock, argListener, argParent, argOperations, argIdSize, argOffset, b);
    }

    @Override
    public Parser operation(Operation operation) {
        return operation(bid, operation, true);
    }

    @Override
    public Parser handleRecord(long id, long[] operands) {
        if (id == 1) {
            // SETBID selects which block subsequent abbreviations are assigned
            return new InformationBlockParser(stream, block, listener, parent, operations, idsize, offset, operands[0]);
        } else {
            return super.handleRecord(id, operands);
        }
    }
}
