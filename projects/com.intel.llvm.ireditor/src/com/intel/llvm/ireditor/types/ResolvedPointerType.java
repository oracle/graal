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

public class ResolvedPointerType extends ResolvedType {
    private final ResolvedType pointedType;
    private final BigInteger addrSpace;

    public ResolvedPointerType(ResolvedType pointedType, BigInteger addrSpace) {
        this.pointedType = pointedType;
        this.addrSpace = addrSpace;
    }

    @Override
    public String toString() {
        String addrSpaceStr = "";

        if (addrSpace.equals(BigInteger.valueOf(-1))) {
            addrSpaceStr = " addrspace(m)";
        } else if (!addrSpace.equals(BigInteger.ZERO)) {
            addrSpaceStr = " addrspace(" + addrSpace.toString() + ")";
        }

        return pointedType.toString() + addrSpaceStr + "*";
    }

    @Override
    public ResolvedType getContainedType(int index) {
        return pointedType;
    }

    @Override
    protected boolean uniAccepts(ResolvedType t) {
        return t instanceof ResolvedPointerType && (addrSpace.longValue() == -1 || addrSpace.equals(((ResolvedPointerType) t).addrSpace)) && pointedType.accepts(((ResolvedPointerType) t).pointedType);
    }

    public BigInteger getAddrSpace() {
        return addrSpace;
    }

    @Override
    public boolean isPointer() {
        return true;
    }

    @Override
    public ResolvedPointerType asPointer() {
        return this;
    }

}
