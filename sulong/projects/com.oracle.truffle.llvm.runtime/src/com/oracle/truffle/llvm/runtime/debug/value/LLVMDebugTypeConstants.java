/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.value;

import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public final class LLVMDebugTypeConstants {

    public static final int BOOLEAN_SIZE = 1;
    public static final int BYTE_SIZE = Byte.SIZE;
    public static final int SHORT_SIZE = Short.SIZE;
    public static final int INTEGER_SIZE = Integer.SIZE;
    public static final int LONG_SIZE = Long.SIZE;

    public static final String BOOLEAN_NAME = "boolean";
    public static final String BYTE_NAME = "byte";
    public static final String SHORT_NAME = "short";
    public static final String INTEGER_NAME = "integer";
    public static final String LONG_NAME = "long";

    public static final int ADDRESS_SIZE = LLVMNode.ADDRESS_SIZE_IN_BYTES * Byte.SIZE;
    public static final String ADDRESS_NAME = "address";

    public static final int FLOAT_SIZE = Float.SIZE;
    public static final int DOUBLE_SIZE = Double.SIZE;
    public static final int LLVM80BIT_SIZE_SUGGESTED = 128;
    public static final int LLVM80BIT_SIZE_ACTUAL = LLVM80BitFloat.BIT_WIDTH;

    public static final String FLOAT_NAME = "float";
    public static final String DOUBLE_NAME = "double";
    public static final String LLVM80BIT_NAME = "80bit float";

    public static String getIntegerKind(int size, boolean signed) {
        String typeName;
        switch (size) {
            case BYTE_SIZE:
                typeName = BYTE_NAME;
                break;

            case SHORT_SIZE:
                typeName = SHORT_NAME;
                break;

            case LONG_SIZE:
                typeName = LONG_NAME;
                break;

            default:
                typeName = INTEGER_NAME;
                break;
        }
        typeName = "signed " + typeName;
        if (!signed) {
            typeName = "un" + typeName;
        }
        return typeName;
    }

    private LLVMDebugTypeConstants() {
    }
}
