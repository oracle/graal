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

import static jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import static jdk.graal.compiler.core.common.type.ArithmeticOpTable.UnaryOp;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SerializableConstant;
import jdk.vm.ci.meta.TriState;

/**
 * Type describing SIMD vectors.
 *
 * A {@code SimdStamp} can have a vector length of one to represent a single value which should be
 * held in a vector register.
 */
public class SimdStamp extends ArithmeticStamp {

    private final Stamp[] components;
    /*
     * Certain operations, like SIMD cuts on constants, should not be constant folded.
     */
    private final boolean mayConstantFold;

    public static SimdStamp broadcast(Stamp component, int length) {
        Stamp[] components = new Stamp[length];
        Arrays.fill(components, component);
        return new SimdStamp(components);
    }

    public SimdStamp(Stamp[] components) {
        this(components, true);
    }

    private SimdStamp(Stamp[] components, boolean mayConstantFold) {
        super(getOpTable(components[0]));
        GraalError.guarantee(hasSameOps(components), Arrays.toString(components));
        this.components = components;
        this.mayConstantFold = mayConstantFold;
    }

    public static SimdStamp createWithoutConstantFolding(Stamp[] components) {
        return new SimdStamp(components, false);
    }

    public static Stamp create(Stamp[] stamps) {
        if (stamps.length == 1) {
            return stamps[0];
        } else {
            return new SimdStamp(stamps);
        }
    }

    public static SimdStamp concat(Stamp a, Stamp b) {
        return ((SimdStamp) a).concat((SimdStamp) b);
    }

    public static SimdStamp singleElement(Stamp element, int length) {
        Stamp[] components = new Stamp[length];
        Stamp zeroStamp = null;
        int bits = PrimitiveStamp.getBits(element);
        if (element instanceof IntegerStamp) {
            zeroStamp = IntegerStamp.create(bits, 0, 0);
        } else if (element instanceof FloatStamp) {
            zeroStamp = StampFactory.forFloat(bits == Float.SIZE ? JavaKind.Float : JavaKind.Double, 0.0, 0.0, true);
        } else {
            GraalError.shouldNotReachHereUnexpectedValue(element); // ExcludeFromJacocoGeneratedReport
        }
        Arrays.fill(components, zeroStamp);
        components[0] = element;
        return new SimdStamp(components);
    }

    @Override
    public void accept(Visitor v) {
        for (Stamp c : components) {
            c.accept(v);
        }
    }

    private static ArithmeticOpTable getOpTable(Stamp stamp) {
        if (stamp instanceof ArithmeticStamp) {
            return SimdOpTable.get(((ArithmeticStamp) stamp).getOps());
        } else {
            return ArithmeticOpTable.EMPTY;
        }
    }

    public ArithmeticOpTable.Op liftScalarOp(ArithmeticOpTable.Op scalarOp) {
        ArithmeticStamp elementStamp = (ArithmeticStamp) components[0];
        return SimdOpTable.get(elementStamp, scalarOp);
    }

    private static boolean hasSameOps(Stamp[] components) {
        if (components[0] instanceof PrimitiveStamp || components[0] instanceof LogicValueStamp || components[0].isPointerStamp()) {
            for (int i = 1; i < components.length; i++) {
                if (!components[0].isCompatible(components[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public int getVectorLength() {
        return components.length;
    }

    public Stamp getComponent(int i) {
        return components[i];
    }

    /**
     * Create a new {@code SimdStamp} based on the supplied component mapping.
     *
     * @param destinationMapping The mapping of elements to produce the new stamp - elements set to
     *            -1 will be zero'd.
     * @return The {@code SimdStamp} with the components re-arranged per the supplied mapping.
     */
    public Stamp permute(int[] destinationMapping) {
        Stamp[] permutedComponents = new Stamp[destinationMapping.length];
        for (int i = 0; i < destinationMapping.length; ++i) {
            if (destinationMapping[i] < 0) {
                if (components[i] instanceof IntegerStamp) {
                    permutedComponents[i] = components[i].unrestricted().improveWith(IntegerStamp.create(((IntegerStamp) components[i]).getBits(), 0, 0));
                } else if (components[i] instanceof FloatStamp) {
                    permutedComponents[i] = components[i].unrestricted().improveWith(FloatStamp.create(((FloatStamp) components[i]).getBits(), 0, 0, true));
                } else if (components[i] instanceof AbstractObjectStamp) {
                    permutedComponents[i] = AbstractObjectStamp.pointerAlwaysNull(components[0].unrestricted());
                } else {
                    permutedComponents[i] = components[i].unrestricted();
                }
            } else {
                permutedComponents[i] = components[destinationMapping[i]];
            }
        }
        return new SimdStamp(permutedComponents, mayConstantFold);
    }

    public static Stamp blend(Stamp x, Stamp y, boolean[] selector) {
        return blend((SimdStamp) x, (SimdStamp) y, selector);
    }

    public static Stamp blend(SimdStamp x, SimdStamp y, boolean[] selector) {
        assert x.getVectorLength() == y.getVectorLength() : "stamps to blend must be the same length";
        assert selector.length == y.getVectorLength() : "selector must have one entry for every entry in the stamps to blend";
        Stamp[] selectedStamps = new Stamp[y.getVectorLength()];
        for (int i = 0; i < selector.length; ++i) {
            selectedStamps[i] = selector[i] == false ? x.getComponent(i) : y.getComponent(i);
        }
        return new SimdStamp(selectedStamps);
    }

    public SimdStamp concat(SimdStamp other) {
        Stamp[] concatComponents = new Stamp[components.length + other.components.length];
        System.arraycopy(components, 0, concatComponents, 0, components.length);
        System.arraycopy(other.components, 0, concatComponents, components.length, other.components.length);
        return new SimdStamp(concatComponents);
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        SimdConstant s = (SimdConstant) c;
        assert components.length == s.getVectorLength() : components + " vs " + s + " " + s.getVectorLength();
        List<Constant> values = s.getValues();
        Stamp[] ret = new Stamp[components.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = components[i].constant(values.get(i), meta);
        }
        return new SimdStamp(ret);
    }

    @Override
    public SerializableConstant deserialize(ByteBuffer buffer) {
        GraalError.guarantee(!isOpmask(this), "Must not be opmask");
        SerializableConstant[] values = new SerializableConstant[components.length];
        for (int i = 0; i < components.length; i++) {
            ArithmeticStamp stamp = (ArithmeticStamp) components[i];
            values[i] = stamp.deserialize(buffer);
        }
        return new SimdConstant(values);
    }

    @Override
    public Constant asConstant() {
        if (!mayConstantFold) {
            return null;
        }
        Constant[] ret = new Constant[components.length];
        for (int i = 0; i < ret.length; i++) {
            Constant c = components[i].asConstant();
            if (c == null) {
                return null;
            } else {
                ret[i] = c;
            }
        }
        return new SimdConstant(ret);
    }

    @Override
    public boolean hasValues() {
        for (Stamp c : components) {
            if (c.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Stamp empty() {
        Stamp[] ret = new Stamp[components.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = components[i].empty();
        }
        return new SimdStamp(ret);
    }

    @Override
    public SimdStamp unrestricted() {
        Stamp[] ret = new Stamp[components.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = components[i].unrestricted();
        }
        return new SimdStamp(ret);
    }

    @Override
    public boolean isUnrestricted() {
        for (int i = 0; i < components.length; i++) {
            if (!components[i].isUnrestricted()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPointerStamp() {
        return components[0].isPointerStamp();
    }

    @Override
    public boolean isIntegerStamp() {
        return components[0].isIntegerStamp();
    }

    @Override
    public boolean isFloatStamp() {
        return components[0].isFloatStamp();
    }

    @Override
    public boolean isObjectStamp() {
        return components[0].isObjectStamp();
    }

    @Override
    public JavaKind getStackKind() {
        /*
         * SIMD values may be introduced by the compiler through auto-vectorization (by the SIMD
         * vectorizer or loop vectorizer) or through the Java Vector API. Values from the Vector API
         * may occur in frame states and must be materializable at deoptimization points. Therefore
         * such values need stamps with a valid Object stack kind.
         *
         * Values from auto-vectorization do not occur in frame states and could have an Illegal
         * kind. However, tracking different kinds of stamps would complicate everything.
         */
        return JavaKind.Object;
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalError.shouldNotReachHere("simd stamp has no Java type"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean isCompatible(Stamp s) {
        if (!(s instanceof SimdStamp)) {
            return false;
        }
        SimdStamp other = (SimdStamp) s;
        if (this.getVectorLength() != other.getVectorLength()) {
            return false;
        }
        for (int i = 0; i < getVectorLength(); i++) {
            if (!components[i].isCompatible(other.components[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isCompatible(Constant constant) {
        return constant instanceof SimdConstant;
    }

    @Override
    public Stamp join(Stamp s) {
        SimdStamp other = (SimdStamp) s;
        assert this.getVectorLength() == other.getVectorLength() : this + " " + this.getVectorLength() + " vs " + other + " " + other.getVectorLength();
        Stamp[] ret = new Stamp[getVectorLength()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = this.getComponent(i).join(other.getComponent(i));
        }
        return new SimdStamp(ret);
    }

    @Override
    public Stamp meet(Stamp s) {
        SimdStamp other = (SimdStamp) s;
        assert this.getVectorLength() == other.getVectorLength() : this + " " + this.getVectorLength() + " vs " + other + " " + other.getVectorLength();
        Stamp[] ret = new Stamp[getVectorLength()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = this.getComponent(i).meet(other.getComponent(i));
        }
        return new SimdStamp(ret);
    }

    @Override
    public LIRKind getLIRKind(LIRKindTool tool) {
        if (isOpmask(this)) {
            /* This should only be used when the architecture supports opmasks. */
            return ((SimdLIRKindTool) tool).getMaskKind(getVectorLength());
        }
        LIRKind element = components[0].getLIRKind(tool);
        return ((SimdLIRKindTool) tool).getSimdKind(getVectorLength(), element);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SimdStamp)) {
            return false;
        }
        SimdStamp other = (SimdStamp) obj;
        if (!Arrays.equals(this.components, other.components)) {
            return false;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + super.hashCode();
        result = prime * result + Arrays.hashCode(components);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("<");
        boolean comma = false;
        for (Stamp s : components) {
            if (comma) {
                ret.append(',');
            }
            ret.append(s.toString());
            comma = true;
        }
        return ret.append('>').toString();
    }

    @Override
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement) {
        int byteStride = PrimitiveStamp.getBits(components[0]) / Byte.SIZE;
        if (byteStride > 0) {
            Constant[] constants = new Constant[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                PrimitiveConstant readConstant = (PrimitiveConstant) components[i].readConstant(provider, base, displacement + i * byteStride);
                if (byteStride < Integer.BYTES && readConstant.getJavaKind() == JavaKind.Int) {
                    /*
                     * PrimitiveStamp.readConstant extended the subword value to int. We need the
                     * narrowed value.
                     */
                    readConstant = JavaConstant.forPrimitiveInt(byteStride * Byte.SIZE, readConstant.asInt());
                }
                constants[i] = readConstant;
            }
            return new SimdConstant(constants);
        }
        return null;
    }

    /**
     * Returns {@code true} iff the given stamp is a mask containing only {@link LogicValueStamp}
     * components. That is, this stamp represents an opmask as on AVX-512. This method is
     * <em>not</em> suitable for determining whether this stamp represents a general "logic value",
     * i.e., the result of a comparison, on platforms that do not use opmasks.
     */
    public static boolean isOpmask(Stamp stamp) {
        if (stamp instanceof SimdStamp) {
            SimdStamp simdStamp = (SimdStamp) stamp;
            for (Stamp s : simdStamp.components) {
                if (!(s instanceof LogicValueStamp)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a SIMD stamp representing the lower half of this stamp. This stamp's length must be a
     * power of 2 greater than 1. The resulting stamp contains this stamp's elements at indices 0 to
     * {@code length/2}.
     */
    public SimdStamp lowerHalf() {
        GraalError.guarantee(components.length > 1 && CodeUtil.isPowerOf2(components.length), "length must be a power of 2 greater than 1");
        Stamp[] lowerStamps = Arrays.copyOfRange(components, 0, components.length / 2, Stamp[].class);
        return new SimdStamp(lowerStamps, mayConstantFold);
    }

    /**
     * Returns a SIMD stamp representing the upper half of this stamp. This stamp's length must be a
     * power of 2 greater than 1. The resulting stamp contains this stamp's elements at indices
     * {@code length/2} to {@code length}.
     */
    public SimdStamp upperHalf() {
        GraalError.guarantee(components.length > 1 && CodeUtil.isPowerOf2(components.length), "length must be a power of 2 greater than 1");
        Stamp[] upperStamps = Arrays.copyOfRange(components, components.length / 2, components.length, Stamp[].class);
        return new SimdStamp(upperStamps, mayConstantFold);
    }

    @Override
    public TriState tryConstantFold(Condition condition, Constant x, Constant y, boolean unorderedIsTrue, ConstantReflectionProvider constantReflection) {
        SimdConstant simdX = (SimdConstant) x;
        SimdConstant simdY = (SimdConstant) y;
        GraalError.guarantee(simdX.getVectorLength() == simdY.getVectorLength(), "can't compare vectors of different length");
        Stamp scalarStamp = getComponent(0);
        /*
         * True if all component-wise comparisons are true, false if all component-wise comparisons
         * are false, unknown otherwise.
         */
        TriState first = scalarStamp.tryConstantFold(condition, simdX.getValue(0), simdY.getValue(0), unorderedIsTrue, constantReflection);
        if (first.isUnknown()) {
            return TriState.UNKNOWN;
        }
        for (int i = 1; i < simdX.getVectorLength(); i++) {
            TriState current = scalarStamp.tryConstantFold(condition, simdX.getValue(i), simdY.getValue(i), unorderedIsTrue, constantReflection);
            if (!first.equals(current)) {
                return TriState.UNKNOWN;
            }
        }
        return first;
    }

    /**
     * Because the ReinterpretNode does not know of SimdStamps, this method can be used to safely
     * reinterpret logic masks.
     */
    public static ValueNode reinterpretMask(SimdStamp toStamp, ValueNode from, NodeView view) {
        SimdStamp fromStamp = (SimdStamp) from.stamp(view);
        GraalError.guarantee(isOpmask(toStamp) && isOpmask(fromStamp), "Expected logic masks!");
        GraalError.guarantee(toStamp.getVectorLength() == fromStamp.getVectorLength(), "Can only reinterpret between logic masks with matching lengths!");
        return ReinterpretNode.create(toStamp, from, view);
    }

    public static final ArithmeticOpTable OPMASK_OPS = new ArithmeticOpTable(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new UnaryOp.Not() {

                        @Override
                        public Constant foldConstant(Constant c) {
                            return LogicValueConstant.ofBoolean(!((LogicValueConstant) c).value());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s) {
                            if (s.isEmpty() || s.isUnrestricted()) {
                                return s;
                            } else if (s.equals(LogicValueStamp.FALSE)) {
                                return LogicValueStamp.TRUE;
                            } else {
                                GraalError.guarantee(s.equals(LogicValueStamp.TRUE), "%s", s);
                                return LogicValueStamp.FALSE;
                            }
                        }
                    },
                    new BinaryOp.And(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            return LogicValueConstant.ofBoolean(((LogicValueConstant) const1).value() & ((LogicValueConstant) const2).value());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            GraalError.guarantee(s1 instanceof LogicValueStamp && s2 instanceof LogicValueStamp, "%s - %s", s1, s2);
                            if (s1.isEmpty() || s2.isEmpty()) {
                                return LogicValueStamp.EMPTY;
                            }
                            if (s1.equals(LogicValueStamp.FALSE) || s2.equals(LogicValueStamp.FALSE)) {
                                return LogicValueStamp.FALSE;
                            }
                            if (s1.isUnrestricted() || s2.isUnrestricted()) {
                                return LogicValueStamp.UNRESTRICTED;
                            }
                            GraalError.guarantee(s1.equals(LogicValueStamp.TRUE) && s2.equals(LogicValueStamp.TRUE), "%s - %s", s1, s2);
                            return LogicValueStamp.TRUE;
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            return n.equals(LogicValueConstant.TRUE);
                        }
                    },
                    new BinaryOp.Or(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            return LogicValueConstant.ofBoolean(((LogicValueConstant) const1).value() | ((LogicValueConstant) const2).value());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            GraalError.guarantee(s1 instanceof LogicValueStamp && s2 instanceof LogicValueStamp, "%s - %s", s1, s2);
                            if (s1.isEmpty() || s2.isEmpty()) {
                                return LogicValueStamp.EMPTY;
                            }
                            if (s1.equals(LogicValueStamp.TRUE) || s2.equals(LogicValueStamp.TRUE)) {
                                return LogicValueStamp.TRUE;
                            }
                            if (s1.isUnrestricted() || s2.isUnrestricted()) {
                                return LogicValueStamp.UNRESTRICTED;
                            }
                            GraalError.guarantee(s1.equals(LogicValueStamp.FALSE) && s2.equals(LogicValueStamp.FALSE), "%s - %s", s1, s2);
                            return LogicValueStamp.FALSE;
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            return n.equals(LogicValueConstant.FALSE);
                        }
                    },
                    new BinaryOp.Xor(true, true) {

                        @Override
                        public Constant foldConstant(Constant const1, Constant const2) {
                            return LogicValueConstant.ofBoolean(((LogicValueConstant) const1).value() ^ ((LogicValueConstant) const2).value());
                        }

                        @Override
                        protected Stamp foldStampImpl(Stamp s1, Stamp s2) {
                            GraalError.guarantee(s1 instanceof LogicValueStamp && s2 instanceof LogicValueStamp, "%s - %s", s1, s2);
                            if (s1.isEmpty() || s2.isEmpty()) {
                                return LogicValueStamp.EMPTY;
                            }
                            if (s1.isUnrestricted() || s2.isUnrestricted()) {
                                return LogicValueStamp.UNRESTRICTED;
                            }
                            if (s1.equals(LogicValueStamp.FALSE)) {
                                return s2;
                            } else if (s2.equals(LogicValueStamp.FALSE)) {
                                return s1;
                            } else {
                                GraalError.guarantee(s1.equals(LogicValueStamp.TRUE) && s2.equals(LogicValueStamp.TRUE), "%s - %s", s1, s2);
                                return LogicValueStamp.FALSE;
                            }
                        }

                        @Override
                        public boolean isNeutral(Constant n) {
                            return n.equals(LogicValueConstant.FALSE);
                        }

                        @Override
                        public LogicValueConstant getZero(Stamp s) {
                            return LogicValueConstant.FALSE;
                        }
                    },
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ArithmeticOpTable.ReinterpretOp() {
                        @Override
                        public Constant foldConstant(Stamp resultStamp, Constant constant) {
                            throw GraalError.unimplementedOverride();
                        }

                        @Override
                        public Stamp foldStamp(Stamp resultStamp, Stamp input) {
                            if (input.isEmpty()) {
                                return resultStamp.empty();
                            }
                            SimdStamp simdIn = (SimdStamp) input;
                            SimdStamp simdResult = (SimdStamp) resultStamp;
                            GraalError.guarantee(simdIn.getComponent(0) instanceof LogicValueStamp && simdResult.getComponent(0) instanceof LogicValueStamp,
                                            "Expected vector logic masks");
                            GraalError.guarantee(simdIn.getVectorLength() == simdResult.getVectorLength(), "Mismatching mask lengths!");
                            return resultStamp;
                        }
                    },
                    null,
                    null);
}
