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

public class ResolvedVectorType extends ResolvedAnyVectorType {
    private final int size;

    public ResolvedVectorType(int size, ResolvedType elementType) {
        super(elementType);
        this.size = size;
    }

    @Override
    public BigInteger getBits() {
        return BigInteger.valueOf(size).multiply(elementType.getBits());
    }

    @Override
    public String toString() {
        return "<" + size + " x " + elementType.toString() + ">";
    }

    @Override
    public ResolvedType getContainedType(int index) {
        assert index < size;
        return elementType;
    }

    public int getSize() {
        return size;
    }

    @Override
    protected boolean uniAccepts(ResolvedType t) {
        return t instanceof ResolvedVectorType && size == ((ResolvedVectorType) t).size && elementType.accepts(t.getContainedType(0));
    }

    @Override
    public ResolvedVectorType asVector() {
        return this;
    }

}
