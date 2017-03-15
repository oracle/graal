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
package com.oracle.truffle.llvm.parser.listeners;

import com.oracle.truffle.llvm.parser.model.generators.SymbolGenerator;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.records.ValueSymbolTableRecord;

public final class ValueSymbolTable implements ParserListener {

    private final SymbolGenerator generator;

    public ValueSymbolTable(SymbolGenerator generator) {
        this.generator = generator;
    }

    @Override
    public void record(long id, long[] args) {
        ValueSymbolTableRecord record = ValueSymbolTableRecord.decode(id);

        switch (record) {
            case ENTRY:
                final String entryName = Records.toString(args, 1);
                generator.nameEntry((int) args[0], entryName);
                break;

            case BASIC_BLOCK_ENTRY:
                final String blockName = Records.toString(args, 1);
                generator.nameBlock((int) args[0], blockName);
                break;

            case FUNCTION_ENTRY:
                final String functionName = Records.toString(args, 2);
                generator.nameFunction((int) args[0], (int) args[1], functionName);
                break;

            default:
                break;
        }
    }
}
