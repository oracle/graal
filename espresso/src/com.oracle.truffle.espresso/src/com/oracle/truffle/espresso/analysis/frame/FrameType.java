/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.analysis.frame;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;

public abstract class FrameType {
    public static final FrameType INT = new PrimitiveFrameType(JavaKind.Int);
    public static final FrameType FLOAT = new PrimitiveFrameType(JavaKind.Float);
    public static final FrameType LONG = new PrimitiveFrameType(JavaKind.Long);
    public static final FrameType DOUBLE = new PrimitiveFrameType(JavaKind.Double);

    public static final FrameType ILLEGAL = new IllegalFrameType();
    public static final FrameType NULL = new NullFrameType();

    public static final FrameType THROWABLE = new TypedFrameType(Type.java_lang_Throwable);

    private final JavaKind kind;

    protected FrameType(JavaKind kind) {
        this.kind = kind;
    }

    public final JavaKind kind() {
        return kind;
    }

    public abstract Symbol<Type> type();

    public boolean isNull() {
        return false;
    }

    public abstract boolean isPrimitive();

    public abstract boolean isReference();

    public static FrameType forType(Symbol<Type> type) {
        assert type != null;
        if (Types.isPrimitive(type)) {
            return forPrimitive(Types.getJavaKind(type).getStackKind());
        }
        return forRefType(type);
    }

    public static FrameType forPrimitive(JavaKind javaKind) {
        switch (javaKind) {
            case Int:
                return INT;
            case Float:
                return FLOAT;
            case Long:
                return LONG;
            case Double:
                return DOUBLE;
            default:
                throw EspressoError.shouldNotReachHere(javaKind.getJavaName());
        }
    }

    private static FrameType forRefType(Symbol<Type> type) {
        return new TypedFrameType(type);
    }

    @Override
    public int hashCode() {
        return kind().ordinal();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        if (isPrimitive()) {
            return kind().toString();
        }
        return type().toString();
    }
}

final class PrimitiveFrameType extends FrameType {

    PrimitiveFrameType(JavaKind kind) {
        super(kind);
    }

    @Override
    public Symbol<Type> type() {
        return kind().getType();
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isReference() {
        return false;
    }
}

final class IllegalFrameType extends FrameType {
    IllegalFrameType() {
        super(JavaKind.Illegal);
    }

    @Override
    public Symbol<Type> type() {
        return null;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public String toString() {
        return kind().toString();
    }
}

abstract class ReferenceFrameType extends FrameType {
    ReferenceFrameType() {
        super(JavaKind.Object);
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return true;
    }
}

final class NullFrameType extends ReferenceFrameType {
    @Override
    public Symbol<Type> type() {
        return Type.java_lang_Object;
    }

    @Override
    public boolean isNull() {
        return true;
    }
}

final class TypedFrameType extends ReferenceFrameType {
    TypedFrameType(Symbol<Type> type) {
        this.type = type;
    }

    private final Symbol<Type> type;

    @Override
    public Symbol<Type> type() {
        return type;
    }

    @Override
    public int hashCode() {
        return type().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TypedFrameType typed) {
            return this.type() == typed.type();
        }
        return false;
    }
}
