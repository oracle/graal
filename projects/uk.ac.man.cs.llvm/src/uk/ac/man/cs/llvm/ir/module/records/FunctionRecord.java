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
package uk.ac.man.cs.llvm.ir.module.records;

public enum FunctionRecord {
    UNUSED_0,
    DECLAREBLOCKS,
    BINOP,
    CAST,
    GEP_OLD,
    SELECT,
    EXTRACTELT,
    INSERTELT,
    SHUFFLEVEC,
    CMP,
    RET,
    BR,
    SWITCH,
    INVOKE,
    UNUSED_14,
    UNREACHABLE,
    PHI,
    UNUSED_17,
    UNUSED_18,
    ALLOCA,
    LOAD,
    UNUSED_21,
    UNUSED_22,
    VAARG,
    STORE_OLD,
    UNUSED_25,
    EXTRACTVAL,
    INSERTVAL,
    CMP2,
    VSELECT,
    INBOUNDS_GEP_OLD,
    INDIRECTBR,
    UNUSED_32,
    FUNC_CODE_DEBUG_LOC_AGAIN,
    CALL,
    FUNC_CODE_DEBUG_LOC,
    FENCE,
    CMPXCHG_OLD,
    ATOMICRMW,
    RESUME,
    LANDINGPAD_OLD,
    LOADATOMIC,
    STOREATOMIC_OLD,
    GEP,
    STORE,
    STOREATOMIC,
    CMPXCHG,
    LANDINGPAD,
    CLEANUPRET,
    CATCHRET,
    CATCHPAD,
    CLEANUPPAD,
    CATCHSWITCH,
    UNUSED_53,
    UNUSED_54,
    FUNC_CODE_OPERAND_BUNDLE;

    public static FunctionRecord decode(long id) {
        return values()[(int) id];
    }
}
