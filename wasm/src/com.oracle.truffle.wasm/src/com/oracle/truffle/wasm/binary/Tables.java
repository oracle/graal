/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.wasm.binary.exception.WasmException;

public class Tables {
    private static final int INITIAL_TABLES_SIZE = 8;

    @CompilationFinal(dimensions = 2) private Object[][] tables;
    @CompilationFinal(dimensions = 1) private int[] maxSizes;
    private int numTables;

    public Tables() {
        this.tables = new Object[INITIAL_TABLES_SIZE][];
        this.maxSizes = new int[INITIAL_TABLES_SIZE];
        this.numTables = 0;
    }

    private void ensureCapacity() {
        if (numTables == tables.length) {
            final Object[][] updatedGlobals = new Object[tables.length * 2][];
            System.arraycopy(tables, 0, updatedGlobals, 0, tables.length);
            tables = updatedGlobals;
            final int[] updatedMaxSizes = new int[maxSizes.length * 2];
            System.arraycopy(maxSizes, 0, updatedMaxSizes, 0, maxSizes.length);
            maxSizes = updatedMaxSizes;
        }
    }

    public int tableCount() {
        return numTables;
    }

    public int allocateTable(int initSize, int maxSize) {
        ensureCapacity();
        tables[numTables] = new Object[initSize];
        maxSizes[numTables] = maxSize;
        int idx = numTables;
        numTables++;
        return idx;
    }

    public Object[] table(int index) {
        assert index < numTables;
        return tables[index];
    }

    public int maxSizeOf(int index) {
        assert index < numTables;
        return maxSizes[index];
    }

    public void ensureSizeAtLeast(int index, int targetSize) {
        final int maxSize = maxSizeOf(index);
        if (maxSize >= 0 && targetSize > maxSize) {
            throw new WasmException("Table " + index + " cannot be resized to " + targetSize + ", " +
                            "declared maximum size is " + maxSize);
        }
        Object[] table = tables[index];
        if (table.length < targetSize) {
            Object[] ntable = new Object[targetSize];
            System.arraycopy(table, 0, ntable, 0, table.length);
            tables[index] = ntable;
        }
    }
}
