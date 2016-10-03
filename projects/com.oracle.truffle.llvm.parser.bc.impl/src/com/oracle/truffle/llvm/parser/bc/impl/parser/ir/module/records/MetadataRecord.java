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
package com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module.records;

public enum MetadataRecord {
    UNUSED_0,
    STRING,
    VALUE,
    NODE,
    NAME,
    DISTINCT_NODE,
    KIND,
    LOCATION,
    OLD_NODE,
    OLD_FN_NODE,
    NAMED_NODE,
    ATTACHMENT,
    GENERIC_DEBUG,
    SUBRANGE,
    ENUMERATOR,
    BASIC_TYPE,
    FILE,
    DERIVED_TYPE,
    COMPOSITE_TYPE,
    SUBROUTINE_TYPE,
    COMPILE_UNIT,
    SUBPROGRAM,
    LEXICAL_BLOCK,
    LEXICAL_BLOCK_FILE,
    NAMESPACE,
    TEMPLATE_TYPE,
    TEMPLATE_VALUE,
    GLOBAL_VAR,
    LOCAL_VAR,
    EXPRESSION,
    OBJC_PROPERTY,
    IMPORTED_ENTITY,
    MODULE,
    MACRO,
    MACRO_FILE;

    public static MetadataRecord decode(long id) {
        return values()[(int) id];
    }
}
