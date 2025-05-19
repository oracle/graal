/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * Represents a SIMD constant. Elements may be primitive values of the same type or
 * LogicValueConstant.
 */
public class SimdConstant implements SerializableConstant {

    private final Constant[] values;
    private final boolean allSame;
    private final boolean isMask;

    public SimdConstant(Constant[] values) {
        this.values = values;

        /* Determining whether all values are identical. */
        boolean valuesIdentical = true;
        Constant firstType = values[0];
        for (Constant value : values) {
            if (!value.equals(firstType)) {
                valuesIdentical = false;
            }
        }

        this.allSame = valuesIdentical;
        this.isMask = firstType instanceof LogicValueConstant;
    }

    public static SimdConstant broadcast(Constant c, int length) {
        Constant[] values = new Constant[length];
        Arrays.fill(values, c);
        return new SimdConstant(values);
    }

    public static ConstantNode constantNodeForConstants(JavaConstant[] values) {
        Stamp[] stamps = new Stamp[values.length];
        for (int i = 0; i < stamps.length; i++) {
            JavaConstant c = values[i];
            if (c.getJavaKind().isNumericInteger()) {
                /*
                 * Use the kind's actual bits, but at least 8. Boolean vectors would otherwise have
                 * an illegal i1 stamp.
                 */
                int bits = Math.max(c.getJavaKind().getBitCount(), 8);
                stamps[i] = IntegerStamp.create(bits, c.asLong(), c.asLong());
            } else {
                stamps[i] = StampFactory.forConstant(c);
            }
        }
        return new ConstantNode(new SimdConstant(values), new SimdStamp(stamps));
    }

    public static ConstantNode constantNodeForBroadcast(JavaConstant value, int length) {
        JavaConstant[] values = new JavaConstant[length];
        Arrays.fill(values, value);
        return constantNodeForConstants(values);
    }

    /**
     * Return a constant that is the same as {@code vectorConstant}, except that the value at
     * {@code index} is replaced by the new {@code value}. It is the caller's responsibility to
     * ensure that the index is in range, and that the value's type is appropriate.
     */
    public static SimdConstant insert(SimdConstant vectorConstant, int index, JavaConstant value) {
        Constant[] newValues = Arrays.copyOf(vectorConstant.values, vectorConstant.values.length);
        newValues[index] = value;
        return new SimdConstant(newValues);
    }

    public JavaConstant toBitMask() {
        assert getVectorLength() <= 64 : getVectorLength() + " " + this;
        long ret = 0;
        for (Constant element : values) {
            if (!isMask) {
                long e = ((JavaConstant) element).asLong();
                ret = (ret << 1) | (e >>> -1);
            } else {
                long e = ((LogicValueConstant) element).value() ? 1 : 0;
                ret = (ret << 1) | e;
            }
        }
        return JavaConstant.forLong(ret);
    }

    public boolean isAllSame() {
        return allSame;
    }

    public int getVectorLength() {
        return values.length;
    }

    public List<Constant> getValues() {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    public Constant getValue(int idx) {
        assert idx < getVectorLength() : idx + ">=" + getVectorLength() + " " + this;
        return values[idx];
    }

    public long getPrimitiveValue(int idx) {
        assert idx < getVectorLength() : idx + ">=" + getVectorLength() + " " + this;
        if (values[idx] instanceof LogicValueConstant l) {
            return l.value() ? 1 : 0;
        }

        PrimitiveConstant primitiveConstant = (PrimitiveConstant) values[idx];
        return switch (primitiveConstant.getJavaKind().getStackKind()) {
            case Int, Long -> primitiveConstant.asLong();
            case Float -> Float.floatToIntBits(primitiveConstant.asFloat());
            case Double -> Double.doubleToLongBits(primitiveConstant.asDouble());
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(primitiveConstant.getJavaKind().getStackKind()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public boolean isDefaultForKind() {
        for (Constant v : values) {
            if (!v.isDefaultForKind()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toValueString() {
        StringBuilder ret = new StringBuilder("<");
        boolean comma = false;
        for (Constant v : values) {
            if (comma) {
                ret.append(',');
            }
            ret.append(v.toValueString());
            comma = true;
        }
        return ret.append('>').toString();
    }

    @Override
    public String toString() {
        return "SIMD" + toValueString();
    }

    @Override
    public int getSerializedSize() {
        return isMask ? Long.BYTES : ((SerializableConstant) getValue(0)).getSerializedSize() * getVectorLength();
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        if (isMask) {
            buffer.putLong(toBitMask().asLong());
            return;
        }

        for (Constant c : values) {
            ((SerializableConstant) c).serialize(buffer);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(values);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SimdConstant)) {
            return false;
        }
        SimdConstant other = (SimdConstant) o;
        return Arrays.deepEquals(values, other.values);
    }

    public static boolean isAllOnes(Constant constant) {
        if (constant instanceof PrimitiveConstant prim) {
            JavaKind kind = prim.getJavaKind();
            long value;
            if (kind.isNumericInteger()) {
                value = prim.asLong();
            } else if (kind == JavaKind.Float) {
                value = Float.floatToRawIntBits(prim.asFloat());
            } else if (kind == JavaKind.Double) {
                value = Double.doubleToRawLongBits(prim.asDouble());
            } else {
                return false;
            }

            long mask = CodeUtil.mask(kind.getByteCount() * 8);
            return (value & mask) == mask;
        } else if (constant instanceof LogicValueConstant lc) {
            return lc.value();
        } else if (constant instanceof SimdConstant simd) {
            for (Constant element : simd.getValues()) {
                if (!isAllOnes(element)) {
                    return false;
                }
            }
            return true;
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(constant);
        }
    }

    /**
     * Returns a SIMD constant representing the lower half of this constant. This constant's length
     * must be a power of 2 greater than 1. The resulting constant contains this constant's values
     * at indices 0 to {@code length / 2}.
     */
    public SimdConstant lowerHalf() {
        GraalError.guarantee(values.length > 1 && CodeUtil.isPowerOf2(values.length), "length must be a power of 2 greater than 1");
        Constant[] lowerValues = Arrays.copyOfRange(values, 0, values.length / 2, Constant[].class);
        return new SimdConstant(lowerValues);
    }

    /**
     * Returns a SIMD constant representing the upper half of this constant. This constant's length
     * must be a power of 2 greater than 1. The resulting constant contains this constant's values
     * at indices {@code length/2} to {@code length}.
     */
    public SimdConstant upperHalf() {
        GraalError.guarantee(values.length > 1 && CodeUtil.isPowerOf2(values.length), "length must be a power of 2 greater than 1");
        Constant[] upperValues = Arrays.copyOfRange(values, values.length / 2, values.length, Constant[].class);
        return new SimdConstant(upperValues);
    }

    /**
     * Builds a SIMD constant whose elements are 0 or -1 if the corresponding element of the
     * selector is false or true, respectively. Each element has kind {@code maskJavaKind}.
     */
    public static SimdConstant forBitmaskBlendSelector(boolean[] selector, JavaKind maskJavaKind) {
        JavaConstant[] values = new JavaConstant[selector.length];
        JavaConstant falseConstant = JavaConstant.forIntegerKind(maskJavaKind, 0);
        JavaConstant trueConstant = JavaConstant.forIntegerKind(maskJavaKind, -1);
        for (int i = 0; i < selector.length; i++) {
            values[i] = selector[i] ? trueConstant : falseConstant;
        }
        return new SimdConstant(values);
    }

    public static SimdConstant forOpmaskBlendSelector(boolean[] selector) {
        Constant[] values = new Constant[selector.length];
        for (int i = 0; i < selector.length; i++) {
            values[i] = LogicValueConstant.ofBoolean(selector[i]);
        }
        return new SimdConstant(values);
    }
}
