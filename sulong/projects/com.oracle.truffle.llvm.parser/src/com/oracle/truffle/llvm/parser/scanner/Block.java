/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import static com.oracle.truffle.llvm.parser.scanner.Block.ParsingStrategy.EAGER;
import static com.oracle.truffle.llvm.parser.scanner.Block.ParsingStrategy.LAZY;
import static com.oracle.truffle.llvm.parser.scanner.Block.ParsingStrategy.SKIP;

public enum Block {

    ROOT(-1),

    BLOCKINFO(0),

    MODULE(8),
    PARAMATTR(9),
    PARAMATTR_GROUP(10),
    CONSTANTS(11),
    FUNCTION(12, LAZY),
    IDENTIFICATION(13),
    VALUE_SYMTAB(14),
    METADATA(15),
    METADATA_ATTACHMENT(16),
    TYPE(17),
    USELIST(18, SKIP),
    MODULE_STRTAB(19, SKIP),
    FUNCTION_SUMMARY(20, SKIP),
    OPERAND_BUNDLE_TAGS(21, SKIP),
    METADATA_KIND(22),
    STRTAB(23),
    FULL_LTO_GLOBALVAR_SUMMARY(24, SKIP),
    SYMTAB(25),
    SYNC_SCOPE_NAMES(26, SKIP);

    private static final Block[] VALUES = values();

    enum ParsingStrategy {
        EAGER,
        LAZY,
        SKIP
    }

    private final int id;
    private final ParsingStrategy strategy;

    Block(int id) {
        this(id, EAGER);
    }

    Block(int id, ParsingStrategy strategy) {
        this.id = id;
        this.strategy = strategy;
    }

    boolean parseLazily() {
        return strategy == LAZY;
    }

    boolean skip() {
        return strategy == SKIP;
    }

    static Block lookup(long id) {
        for (Block block : VALUES) {
            if (block.id == id) {
                return block;
            }
        }
        throw new IllegalStateException("Unknown BlockID: " + id);
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
