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
package com.oracle.truffle.llvm.parser.bc.impl.parser.ir.attributes;

public enum BuiltinAttribute implements Attribute {
    UNUSED_0(""),
    ALIGNMENT("alignment"),
    ALWAYS_INLINE("?"),
    BY_VAL("byval"),
    INLINE_HINT(null),
    IN_REG("?"),
    MIN_SIZE("?"),
    NAKED("?"),
    NEST("?"),
    NO_ALIAS("?"),
    NO_BUILTIN("?"),
    NO_CAPTURE("?"),
    NO_DUPLICATE("?"),
    NO_IMPLICIT_FLOAT("?"),
    NO_INLINE("?"),
    NON_LAZY_BIND("?"),
    NO_RED_ZONE("?"),
    NO_RETURN("?"),
    NO_UNWIND("nounwind"),
    OPTIMIZE_FOR_SIZE("?"),
    READ_NONE("readnone"),
    READ_ONLY("readonly"),
    RETURNED("?"),
    RETURNS_TWICE("?"),
    S_EXT("signext"),
    STACK_ALIGNMENT("?"),
    STACK_PROTECT("?"),
    STACK_PROTECT_REQ("?"),
    STACK_PROTECT_STRONG("?"),
    STRUCT_RET("?"),
    SANITIZE_ADDRESS("?"),
    SANITIZE_THREAD("?"),
    SANITIZE_MEMORY("?"),
    UW_TABLE("uwtable"),
    Z_EXT("zeroext"),
    BUILTIN("?"),
    COLD("?"),
    OPTIMIZE_NONE("?"),
    IN_ALLOCA("?"),
    NON_NULL("?"),
    JUMP_TABLE("?"),
    DEREFERENCEABLE("?"),
    DEREFERENCEABLE_OR_NULL("?"),
    CONVERGENT("?"),
    SAFESTACK("?"),
    ARGMEMONLY("?"),
    SWIFT_SELF("?"),
    SWIFT_ERROR("?"),
    NO_RECURSE("norecurse");

    public static BuiltinAttribute lookup(long id) {
        return values()[(int) id];
    }

    private final String key;

    BuiltinAttribute(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
