/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.truffle.common;

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
