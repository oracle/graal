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
import org.graalvm.wasm.exception.WasmValidationException;

public class TableRegistry {
    private static final int INITIAL_TABLES_SIZE = 8;

    @CompilationFinal(dimensions = 1) private Table[] tables;
    private int numTables;

    public TableRegistry() {
        this.tables = new Table[INITIAL_TABLES_SIZE];
        this.numTables = 0;
    }

    private void ensureCapacity() {
        if (numTables == tables.length) {
            final Table[] updatedTables = new Table[tables.length * 2];
            System.arraycopy(tables, 0, updatedTables, 0, tables.length);
            tables = updatedTables;
        }
    }

    public int tableCount() {
        return numTables;
    }

    public Table allocateTable(int initSize, int maxSize) {
        ensureCapacity();
        int index = numTables;
        tables[numTables] = new Table(index, initSize, maxSize);
        numTables++;
        return tables[index];
    }

    public Table table(int index) {
        assert index < numTables;
        return tables[index];
    }

    public static final class Table {
        private final int tableIndex;
        private final int maxSize;
        @CompilationFinal(dimensions = 1) private Object[] elements;

        public Table(int tableIndex, int initSize, int maxSize) {
            this.tableIndex = tableIndex;
            this.elements = new Object[initSize];
            this.maxSize = maxSize;
        }

        public void ensureSizeAtLeast(int targetSize) {
            if (maxSize >= 0 && targetSize > maxSize) {
                throw new WasmValidationException("Table " + tableIndex + " cannot be resized to " + targetSize + ", " +
                                "declared maximum size is " + maxSize);
            }
            if (elements.length < targetSize) {
                Object[] newElements = new Object[targetSize];
                System.arraycopy(elements, 0, newElements, 0, elements.length);
                elements = newElements;
            }
        }

        public int tableIndex() {
            return tableIndex;
        }

        public int maxSize() {
            return maxSize;
        }

        public Object[] elements() {
            return elements;
        }

        public void set(int i, WasmFunction function) {
            if (elements[i] != null) {
                throw new WasmValidationException("Table " + tableIndex + " already has an element at index " + i + ".");
            }
            elements[i] = function;
        }
    }
}
