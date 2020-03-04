/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class LLVMSourceFunctionType extends LLVMSourceType {

    private final List<LLVMSourceType> types;

    @TruffleBoundary
    public LLVMSourceFunctionType(List<LLVMSourceType> types) {
        // function type do not require size or offset information since there are no concrete
        // values of them in C/C++/Fortran. they are only used as basis for function pointers
        super(0L, 0L, 0L, null);
        assert types != null;
        this.types = types;
        setName(() -> {
            CompilerDirectives.transferToInterpreter();

            StringBuilder nameBuilder = new StringBuilder(getReturnType().getName()).append("(");

            final List<LLVMSourceType> params = getParameterTypes();
            if (params.size() > 0) {
                nameBuilder.append(params.get(0).getName());
            }
            for (int i = 1; i < params.size(); i++) {
                nameBuilder.append(", ").append(params.get(i).getName());
            }

            if (!isVarArgs()) {
                nameBuilder.append(")");
            } else if (getParameterTypes().size() == 0) {
                nameBuilder.append("...)");
            } else {
                nameBuilder.append(", ...)");
            }

            return nameBuilder.toString();
        });
    }

    @TruffleBoundary
    public LLVMSourceType getReturnType() {
        if (types.size() > 0) {
            return types.get(0);
        } else {
            return LLVMSourceType.VOID;
        }
    }

    @TruffleBoundary
    public List<LLVMSourceType> getParameterTypes() {
        if (types.size() <= 1) {
            return Collections.emptyList();
        } else {
            return types.subList(1, types.size() - (isVarArgs() ? 1 : 0));
        }
    }

    @TruffleBoundary
    public boolean isVarArgs() {
        return types.size() > 1 && types.get(types.size() - 1) == LLVMSourceType.VOID;
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return this;
    }

    /**
     * Helper class used to carry information about function argument locations in source code and
     * bitcode when they mismatch. Cases where that could happen is when the compiler desugars
     * structs, examples:
     *
     * <pre>
     * struct Point { double x; double y; };
     * void func (struct Point p); -> void func(double px, double py);
     * </pre>
     */
    public final class SourceArgumentInformation {
        private final int bitcodeArgIndex;
        private final int sourceArgIndex;
        private final int offset;
        private final int size;

        /**
         * @param bitcodeArgIndex Argument location in bitcode.
         * @param sourceArgIndex Argument location in source code.
         * @param offset The offset in bits of the bitcode argument in the source code (e.g. in a
         *            struct).
         * @param size The size of the argument type in bits.
         */
        SourceArgumentInformation(int bitcodeArgIndex, int sourceArgIndex, int offset, int size) {
            this.bitcodeArgIndex = bitcodeArgIndex;
            this.sourceArgIndex = sourceArgIndex;
            this.offset = offset;
            this.size = size;
        }

        public long getBitcodeArgIndex() {
            return this.bitcodeArgIndex;
        }

        public int getSourceArgIndex() {
            return this.sourceArgIndex;
        }

        /**
         * @return The member offset in bits.
         */
        public int getOffset() {
            return this.offset;
        }

        /**
         * @return The argument type size in bits.
         */
        public int getSize() {
            return this.size;
        }
    }

    /**
     * List carrying information for function arguments that mismatch between the source code and
     * the bitcode. It is expected for arguments that do not have any mismatches to have a null
     * entry here. This list can be null if there's no debugging information (or no recognizable
     * debugging information, e.g. due to a missing implementation) associated with the function.
     */
    private ArrayList<SourceArgumentInformation> sourceArgumentInformationList;

    /**
     * Add information for an argument at the location equal to its bitcode location, any arguments
     * in between shall be set to null (see the
     * {@link LLVMSourceFunctionType#sourceArgumentInformationList}). Nulls representing
     * non-mismatching arguments that come after the last mismatching argument are dealt with in
     * {@link LLVMSourceFunctionType#getSourceArgumentInformation}.
     */
    public void attachSourceArgumentInformation(int bitcodeArgIndex, int sourceArgIndex, int offset, int size) {
        if (sourceArgumentInformationList == null) {
            sourceArgumentInformationList = new ArrayList<>();
        }

        int diff = bitcodeArgIndex + 1 - sourceArgumentInformationList.size();
        assert diff >= 0 || sourceArgumentInformationList.get(bitcodeArgIndex) == null : String.format("sourceArgumentInformation for bitcodeArgIndex %d is already set", bitcodeArgIndex);
        for (; diff > 0; diff--) {
            sourceArgumentInformationList.add(null);
        }

        sourceArgumentInformationList.set(bitcodeArgIndex, new SourceArgumentInformation(bitcodeArgIndex, sourceArgIndex, offset, size));
    }

    public SourceArgumentInformation getSourceArgumentInformation(int index) {
        if (sourceArgumentInformationList == null || index >= sourceArgumentInformationList.size()) {
            return null;
        }

        return sourceArgumentInformationList.get(index);
    }
}
