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

import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.Failure;

import java.util.Arrays;

import static java.lang.Integer.compareUnsigned;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.constants.Sizes.MAX_TABLE_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_TABLE_INSTANCE_SIZE;

public final class WasmTable {
    private final int declaredMinSize;
    private final int declaredMaxSize;
    private final int maxAllowedSize;
    private Object[] elements;

    public WasmTable(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        assert compareUnsigned(declaredMinSize, initialSize) <= 0;
        assert compareUnsigned(initialSize, maxAllowedSize) <= 0;
        assert compareUnsigned(maxAllowedSize, declaredMaxSize) <= 0;
        assert compareUnsigned(maxAllowedSize, MAX_TABLE_INSTANCE_SIZE) <= 0;
        assert compareUnsigned(declaredMaxSize, MAX_TABLE_DECLARATION_SIZE) <= 0;

        this.declaredMinSize = declaredMinSize;
        this.declaredMaxSize = declaredMaxSize;
        this.maxAllowedSize = maxAllowedSize;
        this.elements = new Object[declaredMinSize];
    }

    public void ensureSizeAtLeast(int targetSize) {
        assertUnsignedIntLessOrEqual(targetSize, maxAllowedSize, Failure.TABLE_INSTANCE_SIZE_LIMIT_EXCEEDED);
        if (size() < targetSize) {
            elements = Arrays.copyOf(elements, targetSize);
        }
    }

    /**
     * The current size of this table instance.
     */
    public int size() {
        return elements.length;
    }

    /**
     * The maximum practical size of this table instance.
     * <p>
     * It is the minimum between {@link #declaredMaxSize the limit defined in the module binary},
     * {@link Sizes#MAX_TABLE_INSTANCE_SIZE the GraalWasm limit} and any additional limit (the JS
     * API for example has lower limits).
     * <p>
     * This is different from {@link #declaredMaxSize()}, which can be higher.
     */
    public int maxAllowedSize() {
        return maxAllowedSize;
    }

    /**
     * The minimum size of this table as declared in the binary.
     * <p>
     * This is a lower bound on this table's size. This memory can only be imported with a lower or
     * equal minimum size.
     */
    public int declaredMinSize() {
        return declaredMinSize;
    }

    /**
     * The maximum size of this table as declared in the binary.
     * <p>
     * This is an upper bound on this table's size. This table can only be imported with a greater
     * or equal maximum size.
     * <p>
     * This is different from {@link #maxAllowedSize()}, which can be lower.
     */
    public int declaredMaxSize() {
        return declaredMaxSize;
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

    public boolean grow(int delta) {
        final int targetSize = size() + delta;
        if (compareUnsigned(delta, maxAllowedSize) <= 0 && compareUnsigned(targetSize, maxAllowedSize) <= 0) {
            ensureSizeAtLeast(size() + delta);
            return true;
        } else {
            return false;
        }
    }
}
