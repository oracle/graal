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
package uk.ac.man.cs.llvm.bc.blocks;

import uk.ac.man.cs.llvm.bc.Parser;

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
            return values()[(int) id - (MODULE.getId() - MODULE.ordinal())]; // Skip ROOT and BLOCKINFO
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
