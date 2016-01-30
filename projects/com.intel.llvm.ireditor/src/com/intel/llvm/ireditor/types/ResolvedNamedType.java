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

public class ResolvedNamedType extends ResolvedType {
    private final String name;
    private ResolvedType referredType;

    public ResolvedNamedType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public BigInteger getBits() {
        return referredType.getBits();
    }

    @Override
    public ResolvedType getContainedType(int index) {
        return referredType.getContainedType(index);
    }

    @Override
    protected boolean uniAccepts(ResolvedType t) {
        if (t instanceof ResolvedNamedType && name.equals(t.toString())) {
            // Same name means same type
            return true;
        }

        if (referredType instanceof ResolvedStructType && t instanceof ResolvedStructType) {
            // Structures are uniqued by name, so unless this came from a literal value,
            // this is illegal.
            if (!((ResolvedStructType) t).isFromLiteral()) {
                return false;
                // If it is from a literal, it will be covered by the general checks below.
            }
        }

        // Otherwise, compare the referred type
        if (t instanceof ResolvedNamedType) {
            // Wait, if t is a named value itself, we need to compare referred with referred
            return referredType.accepts(((ResolvedNamedType) t).referredType);
        }
        return referredType.accepts(t);
    }

    public void setReferredType(ResolvedType t) {
        referredType = this != t ? t : new ResolvedUnknownType();
    }

    public ResolvedType getReferredType() {
        return referredType;
    }

    @Override
    public boolean isStruct() {
        return referredType.isStruct();
    }

    @Override
    public boolean isVector() {
        return referredType.isVector();
    }

    @Override
    public boolean isPointer() {
        return referredType.isPointer();
    }

    @Override
    public boolean isFloating() {
        return referredType.isFloating();
    }

    @Override
    public boolean isFunction() {
        return referredType.isFloating();
    }

    @Override
    public boolean isInteger() {
        return referredType.isInteger();
    }

    @Override
    public boolean isMetadata() {
        return referredType.isMetadata();
    }

    @Override
    public boolean isVararg() {
        return referredType.isVararg();
    }

    @Override
    public boolean isVoid() {
        return referredType.isVoid();
    }

    @Override
    public boolean isUnknown() {
        return referredType.isUnknown();
    }

    @Override
    public ResolvedPointerType asPointer() {
        return referredType.asPointer();
    }

    @Override
    public ResolvedVectorType asVector() {
        return referredType.asVector();
    }

    @Override
    public ResolvedAnyFunctionType asFunction() {
        return referredType.asFunction();
    }

    @Override
    public ResolvedAnyIntegerType asInteger() {
        return referredType.asInteger();
    }
}
