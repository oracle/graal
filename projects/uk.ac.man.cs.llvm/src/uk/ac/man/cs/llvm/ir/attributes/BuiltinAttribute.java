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
package uk.ac.man.cs.llvm.ir.attributes;

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
