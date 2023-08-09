/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.compiler;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Value returned by {@link TruffleCompilerRuntime#getConstantFieldInfo(ResolvedJavaField)}
 * describing how a field read can be constant folded based on Truffle annotations.
 */
public final class ConstantFieldInfo {

    private final int rawValue;

    /**
     * Denotes a field is annotated by {@code com.oracle.truffle.api.nodes.Node.Child}.
     */
    public static final ConstantFieldInfo CHILD = new ConstantFieldInfo(-1);

    /**
     * Denotes a field is annotated by {@code com.oracle.truffle.api.nodes.Node.Children}.
     */
    public static final ConstantFieldInfo CHILDREN = new ConstantFieldInfo(-2);

    private static final ConstantFieldInfo FINAL_DIMENSIONS_ZERO = new ConstantFieldInfo(0);
    private static final ConstantFieldInfo FINAL_DIMENSIONS_ONE = new ConstantFieldInfo(1);
    private static final ConstantFieldInfo FINAL_DIMENSIONS_TWO = new ConstantFieldInfo(2);

    private ConstantFieldInfo(int rawValue) {
        this.rawValue = rawValue;
    }

    /**
     * Determines if this object is {@link #CHILD}.
     */
    public boolean isChild() {
        return this == CHILD;
    }

    /**
     * Determines if this object is {@link #CHILDREN}.
     */
    public boolean isChildren() {
        return this == CHILDREN;
    }

    /**
     * Gets the number of array dimensions to be marked as compilation final. This value is only
     * non-zero for array type fields.
     *
     * @return a value between 0 and the number of declared array dimensions (inclusive)
     */
    public int getDimensions() {
        return Math.max(0, rawValue);
    }

    /**
     * Gets a {@link ConstantFieldInfo} object for a field.
     *
     * @param dimensions the number of array dimensions to be marked as compilation final
     */
    public static ConstantFieldInfo forDimensions(int dimensions) {
        if (dimensions < 0) {
            throw new IllegalArgumentException("Negative dimensions not allowed");
        }
        switch (dimensions) {
            case 0:
                return FINAL_DIMENSIONS_ZERO;
            case 1:
                return FINAL_DIMENSIONS_ONE;
            case 2:
                return FINAL_DIMENSIONS_TWO;
            default:
                // should be extremely rare
                return new ConstantFieldInfo(dimensions);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ConstantFieldInfo o) {
            return this.rawValue == o.rawValue;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(rawValue);
    }

    @Override
    public String toString() {
        String simpleName = getClass().getSimpleName();
        if (isChild()) {
            return simpleName + "[@Child]";
        } else if (isChildren()) {
            return simpleName + "[@Children]";
        } else {
            return simpleName + "[@CompilationFinal(dimensions=" + getDimensions() + ")]";
        }
    }

}
