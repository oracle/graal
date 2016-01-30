/*
Copyright (c) 2013, Intel Corporation

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of Intel Corporation nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.intel.llvm.ireditor.types;

import java.math.BigInteger;
import java.util.List;

public class ResolvedStructType extends ResolvedAnyStructType {
    private final List<ResolvedType> fieldTypes;
    private final boolean packed;
    private final boolean fromLiteral;

    public ResolvedStructType(List<ResolvedType> fieldTypes, boolean packed, boolean fromLiteral) {
        this.fieldTypes = fieldTypes;
        this.packed = packed;
        this.fromLiteral = fromLiteral;
    }

    @Override
    public BigInteger getBits() {
        BigInteger result = BigInteger.ZERO;
        for (ResolvedType t : fieldTypes) {
            result = result.add(t.getBits());
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (packed) {
            sb.append("<");
        }
        sb.append("{");
        for (ResolvedType t : fieldTypes) {
            if (t != fieldTypes.get(0)) {
                sb.append(", ");
            }
            sb.append(t.toString());
        }
        sb.append("}");
        if (packed) {
            sb.append(">");
        }
        return sb.toString();
    }

    @Override
    public ResolvedType getContainedType(int index) {
        if (index >= fieldTypes.size()) {
            return null;
        }
        return fieldTypes.get(index);
    }

    @Override
    protected boolean uniAccepts(ResolvedType t) {
        return t instanceof ResolvedStructType && packed == ((ResolvedStructType) t).packed && listAccepts(fieldTypes, ((ResolvedStructType) t).fieldTypes);
    }

    public boolean isFromLiteral() {
        return fromLiteral;
    }

    public List<ResolvedType> getFieldTypes() {
        return fieldTypes;
    }

    public boolean isPacked() {
        return packed;
    }

}
