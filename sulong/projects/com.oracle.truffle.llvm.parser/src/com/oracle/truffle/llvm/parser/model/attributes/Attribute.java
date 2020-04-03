/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.attributes;

import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class Attribute {

    public enum Kind {

        NONE,

        ALIGN, // code 1
        ALWAYSINLINE,
        BYVAL,
        INLINEHINT,
        INREG,
        MINSIZE,
        NAKED,
        NEST,
        NOALIAS,
        NOBUILTIN,
        NOCAPTURE,
        NODUPLICATES,
        NOIMPLICITFLOAT,
        NOINLINE,
        NONLAZYBIND,
        NOREDZONE,
        NORETURN,
        NOUNWIND,
        OPTSIZE,
        READNONE,
        READONLY,
        RETURNED,
        RETURNS_TWICE,
        SIGNEXT,
        ALIGNSTACK,
        SSP,
        SSPREQ,
        SSPSTRONG,
        SRET,
        SANITIZE_ADDRESS,
        SANITIZE_THREAD,
        SANITIZE_MEMORY,
        UWTABLE,
        ZEROEXT,
        BUILTIN,
        COLD,
        OPTNONE,
        INALLOCA,
        NONNULL,
        JUMPTABLE,
        DEREFERENCEABLE,
        DEREFERENCEABLE_OR_NULL,
        CONVERGENT,
        SAFESTACK,
        ARGMEMONLY,
        SWIFTSELF,
        SWIFTERROR,
        NORECURSE,
        INACCESSIBLEMEMONLY,
        INACCESSIBLEMEM_OR_ARGMEMONLY,
        ALLOCSIZE,
        WRITEONLY,
        SPECULATABLE;

        private static final Kind[] VALUES = values();

        public static Kind decode(long id) {
            // NONE is not a valid attribute, but this default is in line with llvm
            if (id > 0 && id < VALUES.length) {
                return VALUES[(int) id];
            } else {
                return NONE;
            }
        }

        public String getIrString() {
            return name().toLowerCase();
        }
    }

    public static class KnownAttribute extends Attribute {
        protected final Kind paramAttr;

        public KnownAttribute(Kind paramAttr) {
            this.paramAttr = paramAttr;
        }

        public Kind getAttr() {
            return paramAttr;
        }

        @Override
        public String getIrString() {
            return paramAttr.getIrString();
        }
    }

    public static class KnownTypedAttribute extends KnownAttribute {

        private final Type type;

        public KnownTypedAttribute(Kind paramAttr, Type type) {
            super(paramAttr);
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }

    public static final class KnownIntegerValueAttribute extends KnownAttribute {
        private final int value;

        public KnownIntegerValueAttribute(Kind paramAttr, long value) {
            super(paramAttr);
            if (paramAttr == Kind.ALLOCSIZE) {
                /*
                 * See https://llvm.org/docs/BitCodeFormat.html#paramattr-grp-code-entry-record
                 * <quote> The allocsize attribute has a special encoding for its arguments. Its two
                 * arguments, which are 32-bit integers, are packed into one 64-bit integer value
                 * (i.e. (EltSizeParam << 32) | NumEltsParam), with NumEltsParam taking on the
                 * sentinel value -1 if it is not specified. </quote>
                 *
                 * With the exception of Kind.ALIGN, #value is only used for printing so we are
                 * ignoring the number of elements for now.
                 */
                int eltSizeParam = (int) (value >> 32L);
                // int numEltsParam = (int) value; <-- ignored
                this.value = eltSizeParam;
            } else {
                this.value = (int) value;
            }
        }

        public int getValue() {
            return value;
        }

        @Override
        public String getIrString() {
            if (paramAttr == Kind.ALIGN) {
                return String.format("%s %d", paramAttr.getIrString(), value);
            } else {
                return String.format("%s(%d)", paramAttr.getIrString(), value);
            }
        }
    }

    public static class StringAttribute extends Attribute {
        protected final String stringAttr;

        public StringAttribute(String stringAttr) {
            this.stringAttr = stringAttr;
        }

        @Override
        public String getIrString() {
            return String.format("\"%s\"", stringAttr);
        }
    }

    public static final class StringValueAttribute extends StringAttribute {
        private final String value;

        public StringValueAttribute(String stringAttr, String value) {
            super(stringAttr);
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String getIrString() {
            return String.format("\"%s\"=\"%s\"", stringAttr, value);
        }
    }

    public abstract String getIrString();
}
