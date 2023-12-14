/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.type;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup.SpeculationContextObject;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * A stamp is the basis for a type system.
 */
public abstract class Stamp implements SpeculationContextObject {

    protected Stamp() {
    }

    /**
     * Returns the type of the stamp, guaranteed to be non-null. In some cases, this requires the
     * lookup of class meta data, therefore the {@link MetaAccessProvider} is mandatory.
     */
    public abstract ResolvedJavaType javaType(MetaAccessProvider metaAccess);

    public boolean alwaysDistinct(Stamp other) {
        return join(other).isEmpty();
    }

    /**
     * Gets a Java {@link JavaKind} that can be used to store a value of this stamp on the Java
     * bytecode stack. Returns {@link JavaKind#Illegal} if a value of this stamp can not be stored
     * on the bytecode stack.
     */
    public abstract JavaKind getStackKind();

    /**
     * Gets a platform dependent {@link LIRKind} that can be used to store a value of this stamp.
     */
    public abstract LIRKind getLIRKind(LIRKindTool tool);

    /**
     * Returns the union of this stamp and the given stamp. Typically used to create stamps for phi
     * nodes.
     *
     * @param other The stamp that will enlarge this stamp.
     * @return The union of this stamp and the given stamp.
     */
    public abstract Stamp meet(Stamp other);

    /**
     * Returns the intersection of this stamp and the given stamp.
     *
     * @param other The stamp that will tighten this stamp.
     * @return The intersection of this stamp and the given stamp.
     */
    public abstract Stamp join(Stamp other);

    /**
     * Returns a stamp of the same kind, but allowing the full value range of the kind.
     *
     * {@link #unrestricted()} is the neutral element of the {@link #join(Stamp)} operation.
     */
    public abstract Stamp unrestricted();

    /**
     * Returns a stamp of the same kind, but with no allowed values.
     *
     * {@link #empty()} is the neutral element of the {@link #meet(Stamp)} operation.
     */
    public abstract Stamp empty();

    /**
     * If it is possible to represent single value stamps of this kind, this method returns the
     * stamp representing the single value c. stamp.constant(c).asConstant() should be equal to c.
     * <p>
     * If it is not possible to represent single value stamps, this method returns a stamp that
     * includes c, and is otherwise as narrow as possible.
     */
    public abstract Stamp constant(Constant c, MetaAccessProvider meta);

    /**
     * Test whether two stamps have the same base type.
     */
    public abstract boolean isCompatible(Stamp other);

    /**
     * Check that the constant {@code other} is compatible with this stamp.
     *
     * @param constant
     */
    public abstract boolean isCompatible(Constant constant);

    /**
     * Test whether this stamp has legal values.
     */
    public abstract boolean hasValues();

    /**
     * Tests whether this stamp represents an illegal value.
     */
    public final boolean isEmpty() {
        return !hasValues();
    }

    /**
     * Tests whether this stamp represents all values of this kind.
     */
    public boolean isUnrestricted() {
        return this.equals(this.unrestricted());
    }

    /**
     * Tests whether this stamp represents a pointer value.
     */
    public boolean isPointerStamp() {
        return this instanceof AbstractPointerStamp;
    }

    /**
     * Tests whether this stamp represents an integer value.
     */
    public boolean isIntegerStamp() {
        return this instanceof IntegerStamp;
    }

    /**
     * Tests whether this stamp represents a floating-point value.
     */
    public boolean isFloatStamp() {
        return this instanceof FloatStamp;
    }

    /**
     * Tests whether this stamp represents an Object value.
     */
    public boolean isObjectStamp() {
        return this instanceof AbstractObjectStamp;
    }

    /**
     * Tests whether this stamp represents a pointer that is not an Object value.
     */
    public boolean isNonObjectPointerStamp() {
        return isPointerStamp() && !isObjectStamp();
    }

    /**
     * If this stamp represents a single value, the methods returns this single value. It returns
     * null otherwise.
     *
     * @return the constant corresponding to the single value of this stamp and null if this stamp
     *         can represent less or more than one value.
     */
    public Constant asConstant() {
        return null;
    }

    /**
     * Read a value of this stamp from memory.
     *
     * @return the value read or null if the value can't be read for some reason.
     */
    public abstract Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement);

    /**
     * Read a value of this stamp from memory where the underlying storage might be of a different
     * size. {@code accessStamp} is used to specify the size of access if it differs from the size
     * of the result.
     *
     * @return the value read or null if the value can't be read for some reason
     * @throws IllegalArgumentException if this stamp does not support reading a value of the size
     *             specified by {@code accessStamp}
     */
    public Constant readConstant(MemoryAccessProvider provider, Constant base, long displacement, Stamp accessStamp) {
        if (this.equals(accessStamp)) {
            return readConstant(provider, base, displacement);
        }
        throw new IllegalArgumentException("Access mismatch:" + this + " != " + accessStamp);
    }

    /**
     * Tries to improve this stamp with the stamp given as parameter. If successful, returns the new
     * improved stamp. Otherwise, returns a stamp equal to this.
     *
     * @param other the stamp that should be used to improve this stamp
     * @return the newly improved stamp or a stamp equal to {@code this} if an improvement was not
     *         possible
     */
    public abstract Stamp improveWith(Stamp other);

    /**
     * @return if this stamp can be improved by the other stamp, i.e., it can be made more precise.
     */
    public boolean canBeImprovedWith(Stamp other) {
        return !improveWith(other).equals(this);
    }

    /**
     * Tries to improve this stamp with the stamp given as parameter. If successful, returns the new
     * improved stamp. Otherwise, returns null.
     *
     * @param other the stamp that should be used to improve this stamp
     * @return the newly improved stamp or {@code null} if an improvement was not possible
     */
    public final Stamp tryImproveWith(Stamp other) {
        Stamp improved = improveWith(other);
        if (improved.equals(this)) {
            return null;
        }
        return improved;
    }

    public boolean neverDistinct(Stamp other) {
        Constant constant = this.asConstant();
        if (constant != null) {
            Constant otherConstant = other.asConstant();
            return otherConstant != null && constant.equals(otherConstant);
        }
        return false;
    }

    /**
     * Tries to constant fold the given condition when applied to the constants {@code x} and
     * {@code y}. The constants must be of a type that matches this stamp. If the constants are
     * primitives, this is equivalent to calling
     * {@link Condition#foldCondition(PrimitiveConstant, PrimitiveConstant, boolean)}.
     * <p/>
     *
     * As some kinds of values might not be strictly orderable, this method may return
     * {@link TriState#UNKNOWN}.
     */
    public TriState tryConstantFold(Condition condition, Constant x, Constant y, boolean unorderedIsTrue, ConstantReflectionProvider constantReflection) {
        if (x instanceof PrimitiveConstant) {
            PrimitiveConstant lp = (PrimitiveConstant) x;
            PrimitiveConstant rp = (PrimitiveConstant) y;
            return TriState.get(condition.foldCondition(lp, rp, unorderedIsTrue));
        } else {
            Boolean equal = constantReflection.constantEquals(x, y);
            if (equal == null) {
                return TriState.UNKNOWN;
            }
            switch (condition) {
                case EQ:
                    return TriState.get(equal.booleanValue());
                case NE:
                    return TriState.get(!equal.booleanValue());
                default:
                    return TriState.UNKNOWN;
            }
        }
    }

    /**
     * Convert a Stamp into a representation that can be resolved symbolically into the original
     * stamp. If this stamp contains no references to JVMCI types then simply return null.
     */
    public SymbolicJVMCIReference<? extends Stamp> makeSymbolic() {
        return null;
    }

    @Override
    public abstract String toString();
}
