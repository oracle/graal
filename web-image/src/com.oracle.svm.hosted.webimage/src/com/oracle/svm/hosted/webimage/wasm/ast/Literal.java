/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast;

import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * A WASM literal value.
 * <p>
 * Just a light wrapper around {@link PrimitiveConstant}.
 */
public final class Literal {
    public final WasmPrimitiveType type;
    PrimitiveConstant primitive;

    private Literal(PrimitiveConstant primitive) {
        JavaKind kind = primitive.getJavaKind();
        assert kind.isNumericInteger() || kind.isNumericFloat() : primitive;
        this.type = WasmUtil.mapPrimitiveType(kind);
        this.primitive = primitive;
    }

    public static Literal defaultForType(WasmPrimitiveType type) {
        JavaKind kind = switch (type) {
            case i32 -> JavaKind.Int;
            case i64 -> JavaKind.Long;
            case f32 -> JavaKind.Float;
            case f64 -> JavaKind.Double;
        };

        return forConstant((PrimitiveConstant) JavaConstant.defaultForKind(kind));
    }

    public static Literal forConstant(PrimitiveConstant constant) {
        return new Literal(constant);
    }

    public static Literal forInt(int value) {
        return new Literal(JavaConstant.forInt(value));
    }

    public static Literal forLong(long value) {
        return new Literal(JavaConstant.forLong(value));
    }

    public static Literal forFloat(float value) {
        return new Literal(JavaConstant.forFloat(value));
    }

    public static Literal forDouble(double value) {
        return new Literal(JavaConstant.forDouble(value));
    }

    public int getI32() {
        assert type == WasmPrimitiveType.i32 : type;
        return primitive.asInt();
    }

    public long getI64() {
        assert type == WasmPrimitiveType.i64 : type;
        return primitive.asLong();
    }

    public float getF32() {
        assert type == WasmPrimitiveType.f32 : type;
        return primitive.asFloat();
    }

    public double getF64() {
        assert type == WasmPrimitiveType.f64 : type;
        return primitive.asDouble();
    }

    /**
     * Renders this literal according to the WASM text format.
     * <p>
     * Ref: https://webassembly.github.io/spec/core/text/values.html
     */
    public String asText() {
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case i32 -> sb.append("0x").append(Integer.toHexString(getI32()));
            case i64 -> sb.append("0x").append(Long.toHexString(getI64()));
            case f32 -> {
                float f = getF32();
                if (Float.isNaN(f)) {
                    sb.append("nan");
                } else if (f == Float.NEGATIVE_INFINITY) {
                    sb.append("-inf");
                } else if (f == Float.POSITIVE_INFINITY) {
                    sb.append("+inf");
                } else {
                    sb.append(f);
                }
            }
            case f64 -> {
                double d = getF64();
                if (Double.isNaN(d)) {
                    sb.append("nan");
                } else if (d == Double.NEGATIVE_INFINITY) {
                    sb.append("-inf");
                } else if (d == Double.POSITIVE_INFINITY) {
                    sb.append("+inf");
                } else {
                    sb.append(d);
                }
            }
            default -> throw GraalError.shouldNotReachHere(type.name()); // ExcludeFromJacocoGeneratedReport
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "Literal{" + type + ", " + asText() + "}";
    }
}
