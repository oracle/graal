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
package com.oracle.truffle.llvm.parser.bc.impl.parser.bc.blocks;

import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.Parser;

public enum Block {
    ROOT(-1, parser -> parser),

    BLOCKINFO(0, parser -> new InformationBlockParser(parser)),

    MODULE(8, parser -> parser),
    PARAMATTR(9, parser -> parser),
    PARAMATTR_GROUP(10, parser -> parser),
    CONSTANTS(11, parser -> parser),
    FUNCTION(12, parser -> parser),
    IDENTIFICATION(13, parser -> parser),
    VALUE_SYMTAB(14, parser -> parser),
    METADATA(15, parser -> parser),
    METADATA_ATTACHMENT(16, parser -> parser),
    TYPE(17, parser -> parser),
    USELIST(18, parser -> parser),
    MODULE_STRTAB(19, parser -> parser),
    FUNCTION_SUMMARY(20, parser -> parser),
    OPERAND_BUNDLE_TAGS(21, parser -> parser),
    METADATA_KIND(22, parser -> parser);

    public static Block lookup(long id) {
        if (id == 0) {
            return BLOCKINFO;
        } else if (id >= MODULE.getId() && id <= METADATA_KIND.getId()) {
            // Skip ROOT and BLOCKINFO
            int index = (int) id - (MODULE.getId() - MODULE.ordinal());
            return values()[index];
        }
        return null;
    }

    @FunctionalInterface
    private interface ParserCreator {

        Parser parser(Parser parser);
    }

    private final int id;

    private final ParserCreator creator;

    Block(int id, ParserCreator creator) {
        this.id = id;
        this.creator = creator;
    }

    public int getId() {
        return id;
    }

    public Parser getParser(Parser parser) {
        return creator.parser(parser);
    }

    @Override
    public String toString() {
        return String.format("%s - #%d", name(), getId());
    }
}
