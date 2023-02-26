/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.parser.LLVMParser;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class DataSectionFactory {

    @CompilationFinal(dimensions = 1) private final int[] globalOffsets;
    @CompilationFinal(dimensions = 1) private final int[] threadLocalGlobalOffsets;
    @CompilationFinal(dimensions = 1) private final boolean[] globalIsReadOnly;

    private DataSection roSection;
    private DataSection rwSection;
    private DataSection threadLocalSection;

    // The index for thread local global objects
    private int globalContainerIndex = -1;
    private int threadLocalGlobalContainerLength = 0;

    public DataSectionFactory(LLVMParserResult result) throws Type.TypeOverflowException {
        DataLayout dataLayout = result.getDataLayout();
        int globalsCount = result.getDefinedGlobals().size();
        int threadLocalGlobalsCount = result.getThreadLocalGlobals().size();
        boolean boxGlobals = result.getRuntime().getNodeFactory().boxGlobals();

        this.globalOffsets = new int[globalsCount];
        this.threadLocalGlobalOffsets = new int[threadLocalGlobalsCount];
        this.globalIsReadOnly = new boolean[globalsCount];

        List<GlobalVariable> definedGlobals = result.getDefinedGlobals();
        List<GlobalVariable> threadLocalGlobals = result.getThreadLocalGlobals();
        roSection = new DataSection(dataLayout);
        rwSection = new DataSection(dataLayout);
        threadLocalSection = new DataSection(dataLayout);

        for (int i = 0; i < globalsCount; i++) {
            GlobalVariable global = definedGlobals.get(i);
            Type type = global.getType().getPointeeType();
            if (boxGlobals && LLVMParser.isSpecialGlobalSlot(type)) {
                globalOffsets[i] = -1; // pointer type
            } else {
                // allocate at least one byte per global (to make the pointers unique)
                if (type.getSize(dataLayout) == 0) {
                    type = PrimitiveType.getIntegerType(8);
                }
                globalIsReadOnly[i] = global.isReadOnly();
                DataSection dataSection = globalIsReadOnly[i] ? roSection : rwSection;
                long offset = dataSection.add(global, type);
                assert offset >= 0;
                if (offset > Integer.MAX_VALUE) {
                    throw CompilerDirectives.shouldNotReachHere("globals section >2GB not supported");
                }
                globalOffsets[i] = (int) offset;
            }
        }

        for (int i = 0; i < threadLocalGlobalsCount; i++) {
            GlobalVariable tlGlobals = threadLocalGlobals.get(i);
            Type type = tlGlobals.getType().getPointeeType();
            if (LLVMParser.isSpecialGlobalSlot(type)) {
                // pointer type
                threadLocalGlobalContainerLength++;
                threadLocalGlobalOffsets[i] = globalContainerIndex;
                globalContainerIndex--;
            } else {
                long offset = threadLocalSection.add(tlGlobals, type);
                assert offset >= 0;
                if (offset > Integer.MAX_VALUE) {
                    throw CompilerDirectives.shouldNotReachHere("globals section >2GB not supported");
                }
                threadLocalGlobalOffsets[i] = (int) offset;
            }
        }
    }

    DataSection getRoSection() {
        return roSection;
    }

    DataSection getRwSection() {
        return rwSection;
    }

    DataSection getThreadLocalSection() {
        return threadLocalSection;
    }

    int[] getGlobalOffsets() {
        return globalOffsets;
    }

    boolean[] getGlobalIsReadOnly() {
        return globalIsReadOnly;
    }

    int[] getThreadLocalGlobalOffsets() {
        return threadLocalGlobalOffsets;
    }

    int getThreadLocalGlobalContainerLength() {
        return threadLocalGlobalContainerLength;
    }

    static final class DataSection {

        final DataLayout dataLayout;
        final ArrayList<Type> types = new ArrayList<>();

        private long offset = 0;

        DataSection(DataLayout dataLayout) {
            this.dataLayout = dataLayout;
        }

        long add(GlobalVariable global, Type type) throws Type.TypeOverflowException {
            int alignment = getAlignment(dataLayout, global, type);
            int padding = Type.getPadding(offset, alignment);
            addPaddingTypes(types, padding);
            offset = Type.addUnsignedExact(offset, padding);
            long ret = offset;
            types.add(type);
            offset = Type.addUnsignedExact(offset, type.getSize(dataLayout));
            return ret;
        }

        StructureType getStructureType(String typeName) {
            if (offset > 0) {
                return StructureType.createNamedFromList(typeName, true, types);
            } else {
                return null;
            }
        }
    }

    private static void addPaddingTypes(ArrayList<Type> result, int padding) {
        assert padding >= 0;
        int remaining = padding;
        while (remaining > 0) {
            int size = Math.min(Long.BYTES, Integer.highestOneBit(remaining));
            result.add(PrimitiveType.getIntegerType(size * Byte.SIZE));
            remaining -= size;
        }
    }

    private static int getAlignment(DataLayout dataLayout, GlobalVariable global, Type type) {
        return global.getAlign() > 0 ? 1 << (global.getAlign() - 1) : type.getAlignment(dataLayout);
    }
}
