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
package com.oracle.truffle.llvm.parser.model.enums;

public enum CastOperator {

    TRUNCATE("trunc"),
    ZERO_EXTEND("zext"),
    SIGN_EXTEND("sext"),
    FP_TO_UNSIGNED_INT("fptoui"),
    FP_TO_SIGNED_INT("fptosi"),
    UNSIGNED_INT_TO_FP("uitofp"),
    SIGNED_INT_TO_FP("sitofp"),
    FP_TRUNCATE("fptrunc"),
    FP_EXTEND("fpext"),
    PTR_TO_INT("ptrtoint"),
    INT_TO_PTR("inttoptr"),
    BITCAST("bitcast"),
    ADDRESS_SPACE_CAST("addrspacecast");

    private static final CastOperator[] VALUES = values();

    public static CastOperator decode(int code) {
        if (code >= 0 && code < VALUES.length) {
            return VALUES[code];
        }
        return null;
    }

    private final String irString;

    CastOperator(String irString) {
        this.irString = irString;
    }

    /**
     * Useful to get the llvm ir equivalent string of the enum.
     */
    public String getIrString() {
        return irString;
    }
}
