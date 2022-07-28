/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

public final class InterpreterValuePrimitive extends InterpreterValue {
    private static final InterpreterValuePrimitive INT_MINUS_1 = new InterpreterValuePrimitive(JavaConstant.INT_MINUS_1);
    private static final InterpreterValuePrimitive INT_0 = new InterpreterValuePrimitive(JavaConstant.INT_0);
    private static final InterpreterValuePrimitive INT_1 = new InterpreterValuePrimitive(JavaConstant.INT_1);
    private static final InterpreterValuePrimitive INT_2 = new InterpreterValuePrimitive(JavaConstant.INT_2);
    private static final InterpreterValuePrimitive LONG_0 = new InterpreterValuePrimitive(JavaConstant.LONG_0);
    private static final InterpreterValuePrimitive LONG_1 = new InterpreterValuePrimitive(JavaConstant.LONG_1);
    private static final InterpreterValuePrimitive FLOAT_0 = new InterpreterValuePrimitive(JavaConstant.FLOAT_0);
    private static final InterpreterValuePrimitive FLOAT_1 = new InterpreterValuePrimitive(JavaConstant.FLOAT_1);
    private static final InterpreterValuePrimitive DOUBLE_0 = new InterpreterValuePrimitive(JavaConstant.DOUBLE_0);
    private static final InterpreterValuePrimitive DOUBLE_1 = new InterpreterValuePrimitive(JavaConstant.DOUBLE_1);
    private static final InterpreterValuePrimitive TRUE = new InterpreterValuePrimitive(JavaConstant.TRUE);
    private static final InterpreterValuePrimitive FALSE = new InterpreterValuePrimitive(JavaConstant.FALSE);

    private final PrimitiveConstant primitive;

    /* we will store boxed primitives in instances of this class too */
    private final boolean isBoxed;

    private InterpreterValuePrimitive(PrimitiveConstant primitive) {
        this(primitive, false);
    }

    private InterpreterValuePrimitive(PrimitiveConstant primitive, boolean isBoxed) {
        this.primitive = primitive;
        this.isBoxed = isBoxed;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    public boolean isBoxed() {
        return isBoxed;
    }

    public InterpreterValuePrimitive asUnboxed() {
        if (!isBoxed) {
            throw new IllegalArgumentException();
        }
        return ofPrimitiveConstant(primitive);
    }

    public InterpreterValuePrimitive asBoxed() {
        if (isBoxed) {
            throw new IllegalArgumentException();
        }
        return new InterpreterValuePrimitive(primitive, true);
    }

    @Override
    public PrimitiveConstant asPrimitiveConstant() {
        return primitive;
    }

    @Override
    public JavaKind getJavaKind() {
        return isBoxed ? JavaKind.Object : primitive.getJavaKind();
    }

    @Override
    public boolean isNull() {
        return asPrimitiveConstant().isNull();
    }

    @Override
    public Object asObject() {
        return primitive.asBoxedPrimitive();
    }

    @Override
    public String toString() {
        return primitive.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof InterpreterValuePrimitive)) {
            return false;
        }
        InterpreterValuePrimitive other = (InterpreterValuePrimitive) o;
        return this.primitive.equals(other.primitive);
    }

    @Override
    public int hashCode() {
        return this.primitive.hashCode();
    }

    public static InterpreterValuePrimitive ofPrimitiveConstant(Constant constant) {
        if (!(constant instanceof PrimitiveConstant)) {
            throw new UnsupportedOperationException();
        }

        if (constant.equals(JavaConstant.INT_MINUS_1)) {
            return INT_MINUS_1;
        } else if (constant.equals(JavaConstant.INT_0)) {
            return INT_0;
        } else if (constant.equals(JavaConstant.INT_1)) {
            return INT_1;
        } else if (constant.equals(JavaConstant.INT_2)) {
            return INT_2;
        } else if (constant.equals(JavaConstant.LONG_0)) {
            return LONG_0;
        } else if (constant.equals(JavaConstant.LONG_1)) {
            return LONG_1;
        } else if (constant.equals(JavaConstant.FLOAT_0)) {
            return FLOAT_0;
        } else if (constant.equals(JavaConstant.FLOAT_1)) {
            return FLOAT_1;
        } else if (constant.equals(JavaConstant.DOUBLE_0)) {
            return DOUBLE_0;
        } else if (constant.equals(JavaConstant.DOUBLE_1)) {
            return DOUBLE_1;
        } else if (constant.equals(JavaConstant.TRUE)) {
            return TRUE;
        } else if (constant.equals(JavaConstant.FALSE)) {
            return FALSE;
        } else {
            return new InterpreterValuePrimitive((PrimitiveConstant) constant);
        }
    }

    public static InterpreterValuePrimitive ofInt(int value) {
        return ofPrimitiveConstant(JavaConstant.forInt(value));
    }

    public static InterpreterValuePrimitive ofLong(long value) {
        return ofPrimitiveConstant(JavaConstant.forLong(value));
    }

    public static InterpreterValuePrimitive ofFloat(float value) {
        return ofPrimitiveConstant(JavaConstant.forFloat(value));
    }

    public static InterpreterValuePrimitive ofDouble(float value) {
        return ofPrimitiveConstant(JavaConstant.forDouble(value));
    }

    public static InterpreterValuePrimitive ofBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static InterpreterValuePrimitive defaultForPrimitiveKind(JavaKind kind) {
        if (!kind.isPrimitive()) {
            throw new IllegalArgumentException(kind.toString());
        }

        return ofPrimitiveConstant(JavaConstant.defaultForKind(kind));
    }
}
