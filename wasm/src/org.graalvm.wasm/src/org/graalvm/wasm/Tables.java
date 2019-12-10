/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.exception.WasmException;

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
