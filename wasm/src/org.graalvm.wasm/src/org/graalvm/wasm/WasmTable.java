/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

public final class WasmTable {
    private final int maxSize;
    private Object[] elements;

    public WasmTable(int initSize, int maxSize) {
        this.elements = new Object[initSize];
        this.maxSize = maxSize;
    }

    public void ensureSizeAtLeast(int targetSize) {
        if (maxSize >= 0 && targetSize > maxSize) {
            throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Table cannot be resized to " + targetSize + ", " +
                            "declared maximum size is " + maxSize);
        }
        if (elements.length < targetSize) {
            Object[] newElements = new Object[targetSize];
            System.arraycopy(elements, 0, newElements, 0, elements.length);
            elements = newElements;
        }
    }

    public int size() {
        return elements.length;
    }

    public int maxSize() {
        return maxSize;
    }

    public Object[] elements() {
        return elements;
    }

    public Object get(int index) {
        return elements[index];
    }

    public void set(int index, Object element) {
        elements[index] = element;
    }

    public void initialize(int i, WasmFunctionInstance function) {
        elements[i] = function;
    }

    @SuppressWarnings({"unused", "static-method"})
    public boolean grow(int delta) {
        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, null, "Tables cannot be grown.");
    }
}
